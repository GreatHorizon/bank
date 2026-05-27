package com.example.notifications.controller;

import com.example.notifications.service.NotificationsService;
import com.example.shared.dto.NotificationDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/notifications")
public class NotificationsController {

    final NotificationsService notificationsService;

    public NotificationsController(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }


    @PostMapping
    @PreAuthorize("hasAuthority('notifications_role')")
    void writeEvent(@RequestBody NotificationDto notificationDto) {
        notificationsService.writeNotification(notificationDto);
    }
}
