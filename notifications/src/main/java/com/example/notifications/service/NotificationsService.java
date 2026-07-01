package com.example.notifications.service;

import com.example.notifications.metrics.NotificationMetrics;
import com.example.shared.dto.NotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationsService {

    private final NotificationMetrics notificationMetrics;
    private static final Logger log = LoggerFactory.getLogger(NotificationsService.class);


    public NotificationsService(NotificationMetrics notificationMetrics) {
        this.notificationMetrics = notificationMetrics;
    }

    public void writeNotification(NotificationDto notificationDto) {
        try {
            performWriteNotification(notificationDto);
            log.info("Successfully wrote notification");
        } catch (Exception e) {
            notificationMetrics.failedNotification(notificationDto.login());
            log.error("Error write notification", e);
            throw e;
        }
    }

    private void performWriteNotification(NotificationDto notificationDto) {
        log.info("notification " + notificationDto.toString());
    }
}
