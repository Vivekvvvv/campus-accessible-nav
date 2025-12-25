package com.demo.accessiblenav.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EventStreamService {

    private static final Logger log = LoggerFactory.getLogger(EventStreamService.class);

    /** SSE connection timeout: 30 minutes */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    /** Maximum concurrent SSE connections */
    private static final int MAX_EMITTERS = 200;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(0);

    public SseEmitter subscribe() {
        if (emitters.size() >= MAX_EMITTERS) {
            log.warn("SSE connection limit reached ({}), rejecting new subscription", MAX_EMITTERS);
            SseEmitter rejected = new SseEmitter(0L);
            rejected.completeWithError(new IllegalStateException("Too many concurrent SSE connections"));
            return rejected;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE connection completed, active={}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE connection timed out, active={}", emitters.size());
        });
        emitter.onError((ex) -> {
            emitters.remove(emitter);
            log.debug("SSE connection error: {}, active={}", ex.getMessage(), emitters.size());
        });

        // Send initial hello event to prevent proxies/browsers from assuming the connection is dead
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("message", "connected");
            emitter.send(SseEmitter.event()
                    .id(Objects.requireNonNull(String.valueOf(seq.incrementAndGet())))
                    .name("hello")
                    .data(new SseEvent("HELLO", Instant.now(), data)));
        } catch (IOException ignored) {
            emitters.remove(emitter);
        }

        log.debug("New SSE subscription, active={}", emitters.size());
        return emitter;
    }

    public void publish(String type, Map<String, Object> data) {
        SseEvent payload = new SseEvent(type, Instant.now(), data);
        String id = Objects.requireNonNull(String.valueOf(seq.incrementAndGet()));

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(id)
                        .name("event")
                        .data(payload));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
