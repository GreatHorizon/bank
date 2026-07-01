package com.example.notifications;

import com.example.notifications.service.NotificationsService;
import com.example.shared.dto.NotificationDto;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LoggingConsumer {

    final NotificationsService notificationsService;

    private static final Logger log = LoggerFactory.getLogger(LoggingConsumer.class);

    public LoggingConsumer(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @KafkaListener(
            topics = {
                    "${app.kafka.topics.accounts-events}",
                    "${app.kafka.topics.cash-events}",
                    "${app.kafka.topics.transfer-events}"
            },
            groupId = "${spring.kafka.consumer.group-id:notifications-service}"
    )
    public void listen(ConsumerRecord<String, NotificationDto> record) {
        NotificationDto notification = record.value();

        log.info("Received Kafka event: topic={}, partition={}, offset={}, payload={}",
                record.topic(), record.partition(), record.offset(), notification
        );

        notificationsService.writeNotification(notification);
    }
}
