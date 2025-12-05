package com.netfliz.encoder.constant;

import java.util.Collection;

public class CacheKey {
    public static final String CACHE_VIDEO_PROCESSING_PROGRESS = "video_processing_progress";
    public static final String CACHE_PROCESSING_VIDEO = "processing_video";

    public static String buildKey(String... keys) {
        return String.join("_", keys);
    }

    public static String buildKey(Collection<String> keys) {
        return String.join("_", keys);
    }
}
