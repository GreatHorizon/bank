package com.example.shared.client;

import com.example.shared.dto.NotificationDto;
import org.springframework.kafka.core.KafkaTemplate;

public class NotificationsClient {
    private final String topic;

    private final KafkaTemplate<String, NotificationDto> kafkaTemplate;

    public NotificationsClient(KafkaTemplate<String, NotificationDto> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendNotification(NotificationDto notificationDto) {
        kafkaTemplate.send(topic, notificationDto);
    }
}
