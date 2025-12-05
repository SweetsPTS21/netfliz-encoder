package com.netfliz.encoder.model;

import com.netfliz.encoder.entity.MovieProcessLogsEntity;
import com.netfliz.encoder.utils.JsonUtils;
import lombok.Data;

import java.util.List;

@Data
public class StreamInfoResponse {
    private Long objectId;
    private Integer objectType;
    private String streamUrl;
    private List<String> qualities;

    public static StreamInfoResponse buildFromEntity(MovieProcessLogsEntity from) {
        StreamInfoResponse to = new StreamInfoResponse();
        to.setObjectId(from.getObjectId());
        to.setObjectType(from.getObjectType().getId());

        VideoProcessResult result = JsonUtils.parse(from.getConfigs(), VideoProcessResult.class);
        to.setStreamUrl(result.getMasterPlaylistUrl());
        to.setQualities(result.getProcessedQualities());

        return to;
    }
}
