package com.demo.accessiblenav.emergency;

/**
 * SMS notification service interface for emergency alerts.
 * Replace NoopSmsNotificationService with a real implementation
 * (e.g. Twilio, Aliyun SMS) to enable actual SMS sending.
 */
public interface SmsNotificationService {

    /**
     * Send an SMS to the given phone number.
     * @param phoneNumber recipient phone number
     * @param message     message text
     */
    void send(String phoneNumber, String message);
}
