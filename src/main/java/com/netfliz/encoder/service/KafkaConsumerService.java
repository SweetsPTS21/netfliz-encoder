package com.netfliz.encoder.service;

import com.netfliz.encoder.model.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerService {

    /**
     * Consumer cho VIDEO VIEW events
     * - Cập nhật view count
     * - Lưu lịch sử xem
     * - Trigger recommendation
     */
    @KafkaListener(
            topics = "${kafka.topics.video-view}",
            groupId = "video-view-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeVideoViewEvent(
            @Payload VideoViewEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.info("📺 Processing VideoView - userId: {}, videoId: {}, partition: {}, offset: {}",
                    event.getUserId(), event.getVideoId(), partition, offset);

            // 4. Acknowledge message
            acknowledgment.acknowledge();

            log.debug("✓ VideoView processed successfully - userId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("✗ Error processing VideoView event - userId: {}, videoId: {}",
                    event.getUserId(), event.getVideoId(), e);
            // Không acknowledge để retry
        }
    }

}
