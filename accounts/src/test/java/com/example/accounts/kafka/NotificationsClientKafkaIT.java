package com.example.accounts.kafka;

import com.example.shared.client.NotificationsClient;
import com.example.shared.config.NotificationClientConfig;
import com.example.shared.dto.NotificationDto;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        properties = {
                "app.kafka.producer.enabled=true",
                "app.kafka.topic=notifications-test-events",

                "spring.application.name=accounts",

                "spring.cloud.consul.enabled=false",
                "spring.cloud.consul.config.enabled=false",
                "spring.cloud.consul.config.import-check.enabled=false",
                "spring.cloud.discovery.enabled=false",

                "management.tracing.sampling.probability=0.0",
                "management.tracing.export.zipkin.enabled=false",

                "spring.autoconfigure.exclude=org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration"
        },
        classes = {
                NotificationClientConfig.class,
                NotificationsClientKafkaIT.TestConfig.class
        }
)
class NotificationsClientKafkaIT {

    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.1")
    );

    @BeforeAll
    static void startKafka() {
        kafka.start();
    }

    @AfterAll
    static void stopKafka() {
        kafka.stop();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private NotificationsClient notificationsClient;

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.NOOP;
        }
    }

    @Test
    void shouldSendNotificationToKafka() {
        String topic = "notifications-test-events";

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

        try (Consumer<String, NotificationDto> consumer =
                     new DefaultKafkaConsumerFactory<>(
                             consumerProps,
                             new StringDeserializer(),
                             valueDeserializer
                     ).createConsumer()) {

            consumer.subscribe(List.of(topic));

            ConsumerRecord<String, NotificationDto> record =
                    KafkaTestUtils.getSingleRecord(
                            consumer,
                            topic,
                            Duration.ofSeconds(10)
                    );

            assertThat(record.value()).isNotNull();
            assertThat(record.value().type()).isEqualTo("putCash");
            assertThat(record.value().login()).isEqualTo("admin");
        }
    }
}