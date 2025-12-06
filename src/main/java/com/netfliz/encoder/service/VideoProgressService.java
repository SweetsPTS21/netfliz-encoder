package com.netfliz.encoder.service;

import com.netfliz.encoder.model.VideoProcessingProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProgressService {

    private final SimpMessagingTemplate messagingTemplate;

    // Store progress in memory (có thể dùng Redis cho production)
    private final Map<Long, VideoProcessingProgress> progressStore = new ConcurrentHashMap<>();

    /**
     * Send progress update to specific movie channel
     */
    public void sendProgress(Long movieId, VideoProcessingProgress progress) {
        progress.setLastUpdate(LocalDateTime.now());
        progressStore.put(movieId, progress);

        // Send to topic: /topic/video-progress/{movieId}
        String destination = "/topic/video-progress/" + movieId;
        messagingTemplate.convertAndSend(destination, progress);

        log.debug("Progress update sent for objectId {}: {} - {}%",
                movieId, progress.getCurrentStage(), progress.getOverallProgress());
    }

    /**
     * Update stage
     */
    public void updateStage(Long movieId, VideoProcessingProgress.ProcessingStatus status,
                            String stage, String message, int progress) {
        VideoProcessingProgress progressObj = progressStore.getOrDefault(movieId,
                VideoProcessingProgress.builder()
                        .movieId(movieId)
                        .startTime(LocalDateTime.now())
                        .build()
        );

        progressObj.setStatus(status);
        progressObj.setCurrentStage(stage);
        progressObj.setMessage(message);
        progressObj.setOverallProgress(progress);

        sendProgress(movieId, progressObj);
    }

    /**
     * Update quality encoding progress
     */
    public void updateQualityProgress(Long movieId, String quality,
                                      VideoProcessingProgress.QualityStatus status,
                                      int progress, String operation) {
        VideoProcessingProgress progressObj = progressStore.get(movieId);
        if (progressObj == null) return;

        // Find and update quality
        if (progressObj.getQualities() != null) {
            progressObj.getQualities().stream()
                    .filter(q -> q.getQuality().equals(quality))
                    .findFirst()
                    .ifPresent(q -> {
                        q.setStatus(status);
                        q.setProgress(progress);
                        q.setCurrentOperation(operation);
                    });
        }

        sendProgress(movieId, progressObj);
    }

    /**
     * Update upload progress for quality
     */
    public void updateUploadProgress(Long movieId, String quality,
                                     long uploadedBytes, long totalBytes) {
        VideoProcessingProgress progressObj = progressStore.get(movieId);
        if (progressObj == null) return;

        if (progressObj.getQualities() != null) {
            progressObj.getQualities().stream()
                    .filter(q -> q.getQuality().equals(quality))
                    .findFirst()
                    .ifPresent(q -> {
                        q.setUploadedBytes(uploadedBytes);
                        q.setTotalBytes(totalBytes);
                        q.setProgress((int) ((uploadedBytes * 100) / Math.max(totalBytes, 1)));
                    });
        }

        sendProgress(movieId, progressObj);
    }

    /**
     * Mark as completed
     */
    public void markCompleted(Long movieId, String message) {
        updateStage(movieId, VideoProcessingProgress.ProcessingStatus.COMPLETED,
                "Completed", message, 100);
    }

    /**
     * Mark as failed
     */
    public void markFailed(Long movieId, String errorMessage, String errorDetails) {
        VideoProcessingProgress progress = progressStore.getOrDefault(movieId,
                VideoProcessingProgress.builder().movieId(movieId).build());

        progress.setStatus(VideoProcessingProgress.ProcessingStatus.FAILED);
        progress.setCurrentStage("Failed");
        progress.setErrorMessage(errorMessage);
        progress.setErrorDetails(errorDetails);

        sendProgress(movieId, progress);
    }

    /**
     * Get current progress
     */
    public VideoProcessingProgress getProgress(Long movieId) {
        return progressStore.get(movieId);
    }

    /**
     * Remove progress from store (cleanup after completion)
     */
    public void removeProgress(Long movieId) {
        progressStore.remove(movieId);
    }

    /**
     * Initialize progress with analysis
     */
    public void initializeProgress(Long movieId, String jobId,
                                   VideoProcessingProgress.VideoAnalysis analysis) {
        VideoProcessingProgress progress = VideoProcessingProgress.builder()
                .movieId(movieId)
                .jobId(jobId)
                .status(VideoProcessingProgress.ProcessingStatus.ANALYZING)
                .currentStage("Analyzing video")
                .message("Analyzing video properties...")
                .overallProgress(5)
                .analysis(analysis)
                .startTime(LocalDateTime.now())
                .build();

        sendProgress(movieId, progress);
    }

    /**
     * Set qualities to encode
     */
    public void setQualitiesToEncode(Long movieId, List<String> qualities) {
        VideoProcessingProgress progress = progressStore.get(movieId);
        if (progress == null) return;

        List<VideoProcessingProgress.QualityProgress> qualityProgresses =
                qualities.stream()
                        .map(q -> VideoProcessingProgress.QualityProgress.builder()
                                .quality(q)
                                .status(VideoProcessingProgress.QualityStatus.PENDING)
                                .progress(0)
                                .currentOperation("Waiting")
                                .build())
                        .toList();

        progress.setQualities(qualityProgresses);
        sendProgress(movieId, progress);
    }
}
