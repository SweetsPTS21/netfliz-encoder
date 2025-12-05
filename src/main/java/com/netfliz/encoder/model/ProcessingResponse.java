package com.netfliz.encoder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessingResponse {
    private Long objectId;
    private Integer objectType;
    private String status;
    private Double progress;
}
