package com.netfliz.encoder.config.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.acks}")
    private String acks;

    @Value("${spring.kafka.producer.retries}")
    private Integer retries;

    @Value("${spring.kafka.producer.batch-size}")
    private Integer batchSize;

    @Value("${spring.kafka.producer.linger-ms:10}")
    private Integer lingerMs;

    @Value("${spring.kafka.producer.buffer-memory}")
    private Long bufferMemory;

    @Value("${spring.kafka.producer.compression-type}")
    private String compressionType;

    /**
     * Producer Factory Configuration
     * Cấu hình chung cho tất cả producers
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Basic Configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Reliability Configuration
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Performance Configuration
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Timeout Configuration
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka Template - Standard
     * Template chung cho các event thông thường
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Template - High Priority
     * Template riêng cho các event quan trọng (payment, critical operations)
     * với timeout và retry cao hơn
     */
    @Bean
    public KafkaTemplate<String, String> highPriorityKafkaTemplate() {
        Map<String, Object> configProps = new HashMap<>();

        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Higher reliability
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        // Longer timeouts
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 60000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 300000);

        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(configProps);
        return new KafkaTemplate<>(factory);
    }

    /**
     * Kafka Template - Analytics
     * Template cho analytics với performance cao hơn, reliability thấp hơn
     */
    @Bean
    public KafkaTemplate<String, String> analyticsKafkaTemplate() {
        Map<String, Object> configProps = new HashMap<>();

        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Lower reliability, higher throughput
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Chỉ cần leader acknowledge
        configProps.put(ProducerConfig.RETRIES_CONFIG, 1);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);

        // Larger batches
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize * 4);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 50); // Đợi lâu hơn để batch
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // Nén tốt hơn

        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(configProps);
        return new KafkaTemplate<>(factory);
    }
}