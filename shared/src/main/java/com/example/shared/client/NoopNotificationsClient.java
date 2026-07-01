package com.example.shared.client;

import com.example.shared.dto.NotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopNotificationsClient implements NotificationsClient{

    private static final Logger log = LoggerFactory.getLogger(NoopNotificationsClient.class);

    @Override
    public void sendNotification(NotificationDto notificationDto) {
        log.debug("Notification skipped because Kafka producer is disabled: {}", notificationDto);
    }
}