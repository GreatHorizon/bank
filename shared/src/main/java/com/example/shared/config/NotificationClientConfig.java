package com.example.shared.config;

import com.example.shared.client.KafkaNotificationsClient;
import com.example.shared.client.NoopNotificationsClient;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.NotificationDto;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

public class NotificationClientConfig {
    @Bean
    @ConditionalOnProperty(
            name = "app.kafka.producer.enabled",
            havingValue = "true"
    )
    public ProducerFactory<String, NotificationDto> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.kafka.producer.enabled",
            havingValue = "true"
    )
    public KafkaTemplate<String, NotificationDto> kafkaTemplate(
            ProducerFactory<String, NotificationDto> producerFactory,
            ObjectProvider<ObservationRegistry> observationRegistryProvider
    ) {
        KafkaTemplate<String, NotificationDto> kafkaTemplate = new KafkaTemplate<>(producerFactory);

        observationRegistryProvider.ifAvailable(observationRegistry -> {
            kafkaTemplate.setObservationEnabled(true);
            kafkaTemplate.setObservationRegistry(observationRegistry);
        });

        return kafkaTemplate;
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.kafka.producer.enabled",
            havingValue = "true"
    )
    public KafkaNotificationsClient notificationsClient(
            KafkaTemplate<String, NotificationDto> kafkaTemplate,
            @Value("${app.kafka.topic}") String topic
    ) {
        return new KafkaNotificationsClient(kafkaTemplate, topic);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationsClient.class)
    public NotificationsClient noopNotificationsClient() {
        return new NoopNotificationsClient();
    }
}
