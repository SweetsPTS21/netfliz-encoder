package com.netfliz.encoder.service.impl;

import com.netfliz.encoder.model.UploadVideoResponse;
import com.netfliz.encoder.model.VideoProcessResult;
import com.netfliz.encoder.service.VideoProcessingService;
import com.netfliz.encoder.service.VideoStreamingService;
import jakarta.validation.ValidationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@AllArgsConstructor
public class VideoStreamingServiceImpl implements VideoStreamingService {
    private final VideoProcessingService videoProcessingService;

    @Override
    public UploadVideoResponse uploadVideo(MultipartFile file, String movieId, String title) {
        if (file.isEmpty()) {
            throw new ValidationException("File is empty");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new ValidationException("Invalid file type. Must be video.");
        }

        log.info("Receiving video upload for movie: {}", movieId);

        CompletableFuture<VideoProcessResult> future =
                videoProcessingService.processVideo(file, movieId);

        // Return job ID immediately
        UploadVideoResponse response = new UploadVideoResponse();
        response.setMessage("Video processing started");
        response.setMovieId(movieId);
        response.setStatus("processing");

        // Handle completion asynchronously
        future.thenAccept(result -> {
            log.info("Video processing completed for movie: {}", movieId);
            // Save to database or notify via WebSocket
        }).exceptionally(ex -> {
            log.error("Video processing failed for movie: {}", movieId, ex);
            return null;
        });

        return response;
    }
}
