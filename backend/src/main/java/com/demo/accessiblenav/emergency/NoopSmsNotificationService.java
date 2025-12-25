package com.demo.accessiblenav.emergency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * No-op implementation of SMS notification.
 * Logs the message instead of actually sending it.
 */
@Service
public class NoopSmsNotificationService implements SmsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(NoopSmsNotificationService.class);

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[SMS-NOOP] Would send to {}: {}", phoneNumber, message);
    }
}
