package com.netfliz.encoder.service;

import com.netfliz.encoder.model.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class KafkaProducerService {
    private final KafkaTemplate<String, Object> standardTemplate;

    @Value("${kafka.topics.video-view}")
    private String videoViewTopic;

    public KafkaProducerService(
            @Qualifier("kafkaTemplate") KafkaTemplate<String, Object> standardTemplate) {
        this.standardTemplate = standardTemplate;
    }

    /**
     * Gửi event xem video
     * Sử dụng userId làm key để đảm bảo messages từ cùng user vào cùng partition
     */
    public CompletableFuture<SendResult<String, Object>> sendVideoViewEvent(VideoViewEvent event) {
        event.setEventId(UUID.randomUUID().toString());

        log.info("Sending video view event - userId: {}, videoId: {}",
                event.getUserId(), event.getVideoId());

        return sendMessage(
                standardTemplate,
                videoViewTopic,
                event.getUserId().toString(),
                event,
                "VideoView"
        );
    }
    /**
     * Gửi multiple events cùng lúc (batch)
     * Hữu ích khi cần gửi nhiều events liên quan
     */
    public void sendBatchEvents(VideoViewEvent viewEvent) {
        log.info("Sending batch events for userId: {}", viewEvent.getUserId());

        CompletableFuture.allOf(
                sendVideoViewEvent(viewEvent)
        ).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✓ All batch events sent successfully");
            } else {
                log.error("✗ Some batch events failed", ex);
            }
        });
    }

    /**
     * Method chung để gửi message với callback
     */
    private CompletableFuture<SendResult<String, Object>> sendMessage(
            KafkaTemplate<String, Object> template,
            String topic,
            String key,
            Object event,
            String eventType) {

        CompletableFuture<SendResult<String, Object>> future = template.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("✓ {} event sent - topic: {}, partition: {}, offset: {}",
                        eventType,
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("✗ Failed to send {} event to topic: {}, key: {}",
                        eventType, topic, key, ex);
            }
        });

        return future;
    }

    /**
     * Kiểm tra health của Kafka producer
     */
    public boolean isKafkaAvailable() {
        try {
            // Send một test message
            standardTemplate.send(videoViewTopic, "health-check", "ping")
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            return false;
        }
    }
}
