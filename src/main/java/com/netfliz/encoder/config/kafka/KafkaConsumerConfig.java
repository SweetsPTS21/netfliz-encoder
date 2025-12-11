package com.netfliz.encoder.config.kafka;

import com.netfliz.encoder.advice.CustomKafkaErrorHandler;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    @Value("${spring.kafka.consumer.max-poll-records}")
    private Integer maxPollRecords;

    /**
     * Base Consumer Configuration
     * Cấu hình chung cho tất cả consumers
     */
    private Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();

        // Basic Configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // Deserializer Configuration với Error Handling
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // JSON Deserializer Configuration
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.netfliz.encoder.model.event.BaseEvent");

        // Offset Management
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Fetch Configuration
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        // Session Configuration
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);

        // Isolation Level
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        return props;
    }

    /**
     * Consumer Factory - Standard
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    /**
     * Kafka Listener Container Factory - Standard
     * Dùng cho các event thông thường
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Concurrency - số thread xử lý đồng thời
        factory.setConcurrency(3);

        // Acknowledgment Mode - MANUAL để kiểm soát commit
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Poll Timeout
        factory.getContainerProperties().setPollTimeout(3000);

        // Error Handler
        factory.setCommonErrorHandler(new CustomKafkaErrorHandler());

        return factory;
    }

    /**
     * Batch Listener Container Factory
     * Xử lý messages theo batch cho hiệu năng cao
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> batchListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.setBatchListener(true);  // Enable batch processing

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(3000);
        factory.setCommonErrorHandler(new CustomKafkaErrorHandler());

        return factory;
    }

    /**
     * High Priority Listener Container Factory
     * Dùng cho payment và các event quan trọng
     * - Single thread để đảm bảo ordering
     * - Retry nhiều hơn
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> highPriorityListenerFactory() {
        Map<String, Object> props = consumerConfigs();
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000);  // 10 phút

        ConsumerFactory<String, Object> factory = new DefaultKafkaConsumerFactory<>(props);

        ConcurrentKafkaListenerContainerFactory<String, Object> listenerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();

        listenerFactory.setConsumerFactory(factory);
        listenerFactory.setConcurrency(1);  // Single thread
        listenerFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        listenerFactory.getContainerProperties().setPollTimeout(5000);

        // Retry configuration
        listenerFactory.setCommonErrorHandler(new CustomKafkaErrorHandler());

        return listenerFactory;
    }

    /**
     * Analytics Listener Container Factory
     * Dùng cho analytics - hiệu năng cao, có thể mất một số messages
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> analyticsListenerFactory() {
        Map<String, Object> props = consumerConfigs();

        // Higher throughput settings
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100);

        ConsumerFactory<String, Object> factory = new DefaultKafkaConsumerFactory<>(props);

        ConcurrentKafkaListenerContainerFactory<String, Object> listenerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();

        listenerFactory.setConsumerFactory(factory);
        listenerFactory.setConcurrency(5);  // Nhiều thread
        listenerFactory.setBatchListener(true);
        listenerFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        listenerFactory.getContainerProperties().setPollTimeout(1000);

        return listenerFactory;
    }

    /**
     * Notification Listener Container Factory
     * Nhiều thread để xử lý notification nhanh
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> notificationListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(5);  // 5 threads
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setPollTimeout(3000);
        factory.setCommonErrorHandler(new CustomKafkaErrorHandler());

        return factory;
    }
}