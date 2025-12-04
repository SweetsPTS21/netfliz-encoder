package com.netfliz.encoder.service.impl;

import com.netfliz.encoder.entity.MovieProcessLogsEntity;
import com.netfliz.encoder.entity.enums.ProcessLogsObjectType;
import com.netfliz.encoder.entity.enums.ProcessLogsStatus;
import com.netfliz.encoder.model.UploadVideoResponse;
import com.netfliz.encoder.model.VideoProcessResult;
import com.netfliz.encoder.repository.MovieProcessLogRepository;
import com.netfliz.encoder.service.VideoProcessingService;
import com.netfliz.encoder.service.VideoStreamingService;
import com.netfliz.encoder.utils.JsonUtils;
import jakarta.validation.ValidationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@AllArgsConstructor
public class VideoStreamingServiceImpl implements VideoStreamingService {
    private final VideoProcessingService videoProcessingService;
    private final MovieProcessLogRepository movieProcessLogRepository;

    @Override
    public UploadVideoResponse uploadVideo(MultipartFile file, Long objectId, Integer objectType) {
        validate(file, objectId, objectType);
        log.info("Receiving video upload for movie: {}", objectId);

        CompletableFuture<VideoProcessResult> future =
                videoProcessingService.processVideo(file, objectId);

        // Return job ID immediately
        UploadVideoResponse response = UploadVideoResponse.builder()
                .message("Video processing started")
                .movieId(objectId)
                .status("processing")
                .build();

        // Handle completion asynchronously
        future.thenAccept(result -> {
            log.info("Video processing completed for {}: {}", objectType,  objectId);
            // Save to database or notify via WebSocket
            saveProcessingResult(result, objectType, objectId, 2, 100);
        }).exceptionally(ex -> {
            log.error("Video processing failed for {}: {}", objectType, objectId, ex);
            saveProcessingResult(null, objectType, objectId, 3, 0);
            return null;
        });

        return response;
    }

    private void saveProcessingResult(VideoProcessResult result,
                                      Integer objectType,
                                      Long objectId,
                                      Integer status,
                                      Integer progress) {
        MovieProcessLogsEntity entity = new MovieProcessLogsEntity();
        entity.setObjectId(objectId);
        entity.setObjectType(ProcessLogsObjectType.fromId(objectType));
        entity.setStatus(ProcessLogsStatus.fromId(status));
        entity.setConfigs(JsonUtils.parse(JsonUtils.serialize(result)));
        entity.setProgress(progress);
        movieProcessLogRepository.save(entity);
    }

    private void validate(MultipartFile file, Long objectId, Integer objectType) {
        if (file.isEmpty()) {
            throw new ValidationException("Cần truyền lên file");
        }

        if (Objects.isNull(objectId)) {
            throw new ValidationException("Cần truyền lên objectId");
        }

        if (Objects.isNull(objectType)) {
            throw new ValidationException("Cần truyền lên objectType");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new ValidationException("Invalid file type. Must be video.");
        }

        if (Objects.isNull(ProcessLogsObjectType.fromId(objectType))) {
            throw new ValidationException("Chỉ hỗ trợ movie/episode");
        }
    }
}
