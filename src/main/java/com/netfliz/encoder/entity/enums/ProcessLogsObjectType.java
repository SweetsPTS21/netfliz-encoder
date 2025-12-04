package com.netfliz.encoder.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProcessLogsObjectType {
    MOVIE(1, "movie"),
    EPISODE(2, "episode");

    private final Integer id;
    private final String value;

    public static ProcessLogsObjectType fromId(Integer id) {
        for (ProcessLogsObjectType type : values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
