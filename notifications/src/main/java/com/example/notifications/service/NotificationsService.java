package com.example.notifications.service;

import com.example.shared.dto.NotificationDto;
import org.springframework.stereotype.Service;

@Service
public class NotificationsService {

    public void writeNotification(NotificationDto notificationDto) {
        System.out.println("notification " + notificationDto.toString());
    }
}
