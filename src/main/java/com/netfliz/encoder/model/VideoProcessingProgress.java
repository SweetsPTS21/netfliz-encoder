package com.netfliz.encoder.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VideoProcessingProgress {
    private Long movieId;
    private String jobId;
    private String currentStage;
    private String message;
    private int overallProgress;
    private String errorMessage;
    private String errorDetails;
    private LocalDateTime startTime;
    private LocalDateTime lastUpdate;

    private ProcessingStatus status;
    private VideoAnalysis analysis;
    private List<QualityProgress> qualities;

    @Data
    @Builder
    public static class QualityProgress {
        private String quality;
        private int progress;
        private String currentOperation;
        private long uploadedBytes;
        private long totalBytes;
        private QualityStatus status;
    }

    @Data
    @Builder
    public static class VideoAnalysis {
        private String fileName;
        private String format;
        private long fileSize; // in bytes
        private String fileSizeFormatted; // e.g., "1.5 GB"
        private int width;
        private int height;
        private String resolution; // e.g., "1920x1080"
        private double duration; // in seconds
        private String durationFormatted; // e.g., "01:23:45"
        private int bitRate; // in kbps
        private String videoCodec;
        private String audioCodec;
        private double frameRate;
    }

    public enum QualityStatus {
        PENDING,
        ENCODING,
        UPLOADING,
        COMPLETED,
        FAILED
    }

    public enum ProcessingStatus {
        QUEUED,
        CREATING_PLAYLIST,
        CLEANING_OLD_FILES,
        ANALYZING,
        ENCODING,
        UPLOADING,
        COMPLETED,
        FAILED
    }
}
