package com.netfliz.encoder.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class B2FileInfo {
    private String fileName;
    private String fileId;
    private Long contentLength;
}
