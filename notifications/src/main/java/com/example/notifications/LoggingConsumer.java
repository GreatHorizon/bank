package com.example.notifications;

import com.example.notifications.service.NotificationsService;
import com.example.shared.dto.NotificationDto;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LoggingConsumer {

    final NotificationsService notificationsService;

    public LoggingConsumer(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @KafkaListener(
            topics = {"accounts-events", "cash-events", "transfer-events"},
            groupId = "notifications-service"
    )
    public void listen(ConsumerRecord<String, NotificationDto> record) {
        NotificationDto notification = record.value();

        System.out.println("Received Kafka event");
        System.out.println("topic=" + record.topic());
        System.out.println("partition=" + record.partition());
        System.out.println("offset=" + record.offset());
        System.out.println("payload=" + notification);

        notificationsService.writeNotification(notification);
    }
}
