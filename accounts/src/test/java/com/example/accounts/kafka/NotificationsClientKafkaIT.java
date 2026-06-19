package com.example.accounts.kafka;

import com.example.shared.client.NotificationsClient;
import com.example.shared.config.NotificationClientConfig;
import com.example.shared.dto.NotificationDto;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "app.kafka.producer.enabled=true",
                "app.kafka.topic=notifications-test-events"
        },
        classes = NotificationClientConfig.class
)
class NotificationsClientKafkaIT {

    static KafkaContainer kafka = new KafkaContainer(
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
    NotificationsClient notificationsClient;

    @Test
    void shouldSendNotificationToKafka() {
        NotificationDto dto = new NotificationDto("putCash", 123L, "admin", null);

        notificationsClient.sendNotification(dto);

        Map<String, Object> consumerProps = new HashMap<>();

        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        JacksonJsonDeserializer<NotificationDto> valueDeserializer =
                new JacksonJsonDeserializer<>(NotificationDto.class);

        valueDeserializer.addTrustedPackages("com.example.shared.dto");

        valueDeserializer.setUseTypeHeaders(false);

        Consumer<String, NotificationDto> consumer =
                new DefaultKafkaConsumerFactory<>(
                        consumerProps,
                        new StringDeserializer(),
                        valueDeserializer
                ).createConsumer();

        consumer.subscribe(java.util.List.of("notifications-test-events"));

        ConsumerRecord<String, NotificationDto> record =
                KafkaTestUtils.getSingleRecord(
                        consumer,
                        "notifications-test-events",
                        Duration.ofSeconds(10)
                );

        assertThat(record.value()).isNotNull();

        consumer.close();
    }
}