package com.netfliz.encoder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadVideoResponse {
    private String message;
    private Long movieId;
    private String status;
    private String progress;
}
