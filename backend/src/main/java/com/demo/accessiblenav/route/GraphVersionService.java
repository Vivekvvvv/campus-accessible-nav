package com.demo.accessiblenav.route;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class GraphVersionService {

    private final AtomicLong version = new AtomicLong(1);

    public long current() {
        return version.get();
    }

    public long bump() {
        return version.incrementAndGet();
    }
}
