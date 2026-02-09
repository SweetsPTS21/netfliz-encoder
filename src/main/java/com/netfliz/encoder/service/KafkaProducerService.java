package com.netfliz.encoder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netfliz.encoder.constant.KafkaEventType;
import com.netfliz.encoder.model.event.UpdateMovieAssetEvent;
import com.netfliz.encoder.model.event.VideoViewEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class KafkaProducerService {
    private final KafkaTemplate<String, String> standardTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.update-movie-asset}")
    private String updateMovieAssetTopic;

    @Value("${kafka.topics.video-view}")
    private String videoViewTopic;

    public KafkaProducerService(
            @Qualifier("kafkaTemplate") KafkaTemplate<String, String> standardTemplate,
            ObjectMapper objectMapper) {
        this.standardTemplate = standardTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendUpdateMovieAssetEvent(UpdateMovieAssetEvent event) {
        event.setEventId(UUID.randomUUID().toString());
        var payload = event.getPayload();

        log.info("Sending update movie asset event - movieId: {}", payload.getObjectId());

        sendMessage(
                standardTemplate,
                updateMovieAssetTopic,
                payload.getObjectId().toString(),
                event,
                KafkaEventType.UPDATE_MOVIE_ASSET);
    }

    /**
     * Gửi event xem video
     * Sử dụng userId làm key để đảm bảo messages từ cùng user vào cùng partition
     */
    public CompletableFuture<SendResult<String, String>> sendVideoViewEvent(VideoViewEvent event) {
        event.setEventId(UUID.randomUUID().toString());

        log.info("Sending video view event - userId: {}, videoId: {}",
                event.getUserId(), event.getVideoId());

        return sendMessage(
                standardTemplate,
                videoViewTopic,
                event.getUserId().toString(),
                event,
                KafkaEventType.VIDEO_VIEW);
    }

    /**
     * Gửi multiple events cùng lúc (batch)
     * Hữu ích khi cần gửi nhiều events liên quan
     */
    public void sendBatchEvents(VideoViewEvent viewEvent) {
        log.info("Sending batch events for userId: {}", viewEvent.getUserId());

        CompletableFuture.allOf(
                sendVideoViewEvent(viewEvent)).whenComplete((result, ex) -> {
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
    private CompletableFuture<SendResult<String, String>> sendMessage(
            KafkaTemplate<String, String> template,
            String topic,
            String key,
            Object event,
            String eventType) {

        String jsonValue;
        try {
            jsonValue = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("✗ Failed to serialize {} event to JSON", eventType, e);
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<SendResult<String, String>> future = template.send(topic, key, jsonValue);

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
                    .get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            return false;
        }
    }
}
