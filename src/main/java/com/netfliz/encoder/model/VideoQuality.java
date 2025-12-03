package com.netfliz.encoder.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VideoQuality {
    String name;
    int width;
    int height;
    String videoBitrate;
    String audioBitrate;
}
