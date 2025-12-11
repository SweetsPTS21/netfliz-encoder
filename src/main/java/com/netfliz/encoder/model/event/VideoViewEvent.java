package com.netfliz.encoder.model.event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// Event khi user xem video
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoViewEvent {
    private String eventId;
    private Long userId;
    private Long videoId;
    private String videoTitle;
    private LocalDateTime viewStartTime;
    private Integer watchDuration; // Giây
    private String deviceType;
    private String ipAddress;
    private String quality; // 480p, 720p, 1080p, 4K
}
