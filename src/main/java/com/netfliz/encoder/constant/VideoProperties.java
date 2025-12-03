package com.netfliz.encoder.constant;


import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@EqualsAndHashCode
@Configuration
@ConfigurationProperties(prefix = "video")
public class VideoProperties {
    private Processing processing;
    private List<Quality> qualities;

    @Data
    public static class Processing {
        private String tempDir;
        private String ffmpegPath;
        private int concurrentJobs;
    }

    @Data
    public static class Quality {
        private String name;
        private int width;
        private int height;
        private String videoBitrate;
        private String audioBitrate;
    }
}
