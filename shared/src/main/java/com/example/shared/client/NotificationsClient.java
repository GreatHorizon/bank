package com.example.shared.client;

import com.example.shared.dto.NotificationDto;

public interface NotificationsClient {
    void sendNotification(NotificationDto notificationDto);
}
