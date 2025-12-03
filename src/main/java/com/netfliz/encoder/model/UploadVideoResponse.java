package com.netfliz.encoder.model;

import lombok.Data;

@Data
public class UploadVideoResponse {
    private String message;
    private String movieId;
    private String status;
    private String progress;
}
