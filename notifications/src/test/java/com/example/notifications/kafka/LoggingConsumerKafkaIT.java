package com.example.notifications.kafka;

import com.example.notifications.LoggingConsumer;
import com.example.notifications.config.KafkaConsumerConfig;
import com.example.notifications.service.NotificationsService;
import com.example.shared.client.NotificationsClient;
import com.example.shared.config.NotificationClientConfig;
import com.example.shared.dto.NotificationDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = {
                LoggingConsumer.class,
                KafkaConsumerConfig.class,
                NotificationClientConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "app.kafka.producer.enabled=true",
                "app.kafka.topic=accounts-events",

                "spring.kafka.consumer.group-id=notifications-service-test",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.enable-auto-commit=false",

                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=com.example.shared.dto",
                "spring.kafka.consumer.properties.spring.json.value.default.type=com.example.shared.dto.NotificationDto",
                "spring.kafka.consumer.properties.spring.json.use.type.headers=false"
        }
)
class LoggingConsumerKafkaIT {

    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.1")
    );

    static {
        kafka.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private NotificationsClient notificationsClient;

    @MockitoSpyBean
    private LoggingConsumer loggingConsumer;

    @MockitoBean
    private NotificationsService notificationsService;

    @Test
    void shouldConsumeNotificationFromKafka() {
        NotificationDto dto = new NotificationDto("putCash", 123L, "admin", null);

        notificationsClient.sendNotification(dto);

        verify(loggingConsumer, timeout(10_000).atLeastOnce())
                .listen(argThat(record ->
                        record != null
                                && record.value() != null
                                && record.value().type().equals("putCash")
                                && record.value().amount().equals(123L)
                                && record.value().login().equals("admin")
                ));

        verify(notificationsService, timeout(10_000).atLeastOnce())
                .writeNotification(argThat(notification ->
                        notification != null
                                && notification.type().equals("putCash")
                                && notification.amount().equals(123L)
                                && notification.login().equals("admin")
                ));
    }
}