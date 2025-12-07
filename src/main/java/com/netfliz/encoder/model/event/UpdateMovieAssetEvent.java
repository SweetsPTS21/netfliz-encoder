package com.netfliz.encoder.model.event;

import com.netfliz.encoder.constant.KafkaEventType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UpdateMovieAssetEvent extends BaseEvent<UpdateMovieAssetEvent.MovieAssetPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovieAssetPayload {
        private Long objectId;
        private Integer objectType;
        private Integer assetType; // e.g., "TRAILER", "MOVIE", "TEASER"
        private String format;
        private String url;
        private String drm;
        private String rendition;
        private FilePayload file;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilePayload {
        private String fileCategory;
        private String fileDownloadUri;
        private String fileExtension;
        private String fileName;
        private String fileOwner;
        private Long fileSize;
        private String fileType;
        private String fileUploader;
    }

    @Override
    public String getTopic() {
        return "update-movie-asset";
    }

    // Factory method to create event with payload
    public static UpdateMovieAssetEvent create(MovieAssetPayload payload) {
        return UpdateMovieAssetEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(KafkaEventType.UPDATE_MOVIE_ASSET)
                .timestamp(LocalDateTime.now())
                .source("encoder-service")
                .payload(payload)
                .build();
    }
}
