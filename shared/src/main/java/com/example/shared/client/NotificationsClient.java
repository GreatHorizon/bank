package com.example.shared.client;

import com.example.shared.dto.NotificationDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationsClient {

    private final RestClient restClient;

    public NotificationsClient(@Qualifier("notificationsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void sendNotification(NotificationDto notificationDto) {
        restClient.post()
                .uri("/api/notifications")
                .body(notificationDto)
                .retrieve()
                .toBodilessEntity();
    }
}
