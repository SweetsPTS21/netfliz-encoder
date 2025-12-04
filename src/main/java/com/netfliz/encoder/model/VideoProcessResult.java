package com.netfliz.encoder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoProcessResult {
    Long movieId;
    String masterPlaylistUrl;
    List<String> qualities;
}
