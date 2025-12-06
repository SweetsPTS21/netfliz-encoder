package com.netfliz.encoder.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseEvent<T> {
    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String source;
    private T payload;
    private String correlationId;

    public BaseEvent(T payload) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.payload = payload;
    }

    public abstract String getTopic();

    public String getKey() {
        return this.eventId;
    }

    public boolean shouldRetry(int attempt, Exception exception) {
        // Default retry policy: retry up to 3 times for any exception
        return attempt < 3;
    }
}
