package com.example.notifications.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void failedNotification(String login) {
        Counter.builder("notification_send_failed_total")
                .description("Number of failed notification sending attempts")
                .tag("login", login)
                .register(meterRegistry)
                .increment();
    }
}