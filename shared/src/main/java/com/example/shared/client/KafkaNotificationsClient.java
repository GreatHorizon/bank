package com.example.shared.client;

import com.example.shared.dto.NotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

public class KafkaNotificationsClient implements NotificationsClient {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationsClient.class);

    private final String topic;
    private final KafkaTemplate<String, NotificationDto> kafkaTemplate;

    public KafkaNotificationsClient(
            KafkaTemplate<String, NotificationDto> kafkaTemplate,
            String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendNotification(NotificationDto notificationDto) {
        log.info("Sending notification to Kafka: topic={}, type={}, login={}",
                topic,
                notificationDto.type(),
                notificationDto.login()
        );

        CompletableFuture<?> future = kafkaTemplate.send(topic, notificationDto);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send notification to Kafka: topic={}, type={}, login={}, reason={}",
                        topic,
                        notificationDto.type(),
                        notificationDto.login(),
                        ex.getMessage(),
                        ex
                );
            } else {
                log.info("Notification sent to Kafka successfully: topic={}, type={}, login={}",
                        topic,
                        notificationDto.type(),
                        notificationDto.login()
                );
            }
        });
    }
}
