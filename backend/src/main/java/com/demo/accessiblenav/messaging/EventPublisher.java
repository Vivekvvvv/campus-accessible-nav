package com.demo.accessiblenav.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 领域事件发布服务
 * 使用 Spring 的事件机制，可以无缝切换到消息队列
 */
@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public EventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 发布领域事件
     */
    public void publish(DomainEvent event) {
        log.info("发布事件: type={}, aggregateId={}, eventId={}",
                event.getEventType(), event.getAggregateId(), event.getEventId());

        applicationEventPublisher.publishEvent(event);
    }

    /**
     * 发布多个事件
     */
    public void publishAll(Iterable<? extends DomainEvent> events) {
        for (DomainEvent event : events) {
            publish(event);
        }
    }
}
