package com.netfliz.encoder.advice;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomKafkaErrorHandler implements CommonErrorHandler {

    /**
     * Xử lý lỗi khi consumer không thể xử lý message
     */
    @Override
    public boolean handleOne(
            Exception thrownException,
            ConsumerRecord<?, ?> record,
            Consumer<?, ?> consumer,
            MessageListenerContainer container) {

        log.error("Error processing message - Topic: {}, Partition: {}, Offset: {}, Key: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                thrownException);

        // Có thể gửi message vào DLQ (Dead Letter Queue)
        sendToDeadLetterQueue(record, thrownException);

        // Return true để acknowledge message (không retry)
        // Return false để skip acknowledge (sẽ retry)
        return true;
    }

    /**
     * Xử lý lỗi chung cho batch processing
     */
    @Override
    public void handleOtherException(
            Exception thrownException,
            Consumer<?, ?> consumer,
            MessageListenerContainer container,
            boolean batchListener) {

        log.error("Unexpected error in Kafka consumer", thrownException);
    }

    /**
     * Gửi failed message vào Dead Letter Queue
     */
    private void sendToDeadLetterQueue(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            log.info("Sending message to DLQ - Topic: {}, Offset: {}",
                    record.topic(), record.offset());

            // TODO: Implement DLQ logic
            // Có thể lưu vào database hoặc gửi vào Kafka DLQ topic

        } catch (Exception e) {
            log.error("Failed to send message to DLQ", e);
        }
    }
}
