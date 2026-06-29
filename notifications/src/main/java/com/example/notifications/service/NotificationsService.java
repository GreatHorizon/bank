package com.example.notifications.service;

import com.example.notifications.metrics.NotificationMetrics;
import com.example.shared.dto.NotificationDto;
import org.springframework.stereotype.Service;

@Service
public class NotificationsService {

    private final NotificationMetrics notificationMetrics;

    public NotificationsService(NotificationMetrics notificationMetrics) {
        this.notificationMetrics = notificationMetrics;
    }

    public void writeNotification(NotificationDto notificationDto) {
        try {
            performWriteNotification(notificationDto);
        } catch (Exception e) {
            notificationMetrics.failedNotification(notificationDto.login());

            throw e;
        }
    }

    private void performWriteNotification(NotificationDto notificationDto) {
        System.out.println("notification " + notificationDto.toString());
    }
}
