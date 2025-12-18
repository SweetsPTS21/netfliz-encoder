package com.netfliz.encoder.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MovieAssetType {
    VIDEO(1, "Video"),
    TRAILER(2, "Trailer"),
    SUBTITLE(3, "Subtitle"),
    VIDEO_ENCODE(4, "Video Encode");

    private final Integer id;
    private final String description;

    public static MovieAssetType fromId(Integer id) {
        for (MovieAssetType type : MovieAssetType.values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
