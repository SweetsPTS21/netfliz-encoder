package com.netfliz.encoder.model;

import lombok.Data;

@Data
public class VideoMetadata {
    private Integer width;
    private Integer height;
    private String videoCodec;
    private Integer bitrate;
    private Double fps;
}
