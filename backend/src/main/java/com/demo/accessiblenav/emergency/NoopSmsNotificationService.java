package com.demo.accessiblenav.emergency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-op implementation of SMS notification.
 * Active when app.sms.provider=noop (default).
 * Replace with a real implementation (e.g. Twilio, Aliyun SMS)
 * by setting app.sms.provider=twilio or app.sms.provider=aliyun
 * and providing a corresponding @Service bean.
 */
@Service
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "noop", matchIfMissing = true)
public class NoopSmsNotificationService implements SmsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(NoopSmsNotificationService.class);

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[SMS-NOOP] Would send to {}: {}", phoneNumber, message);
    }
}
