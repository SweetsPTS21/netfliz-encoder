package com.netfliz.encoder.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProcessLogsStatus {
    PROCESSING(1, "processing"),
    COMPLETED(2, "completed"),
    FAILED(3, "failed");

    private final Integer id;
    private final String name;

    public static ProcessLogsStatus fromId(Integer id) {
        for (ProcessLogsStatus status : values()) {
            if (status.getId().equals(id)) {
                return status;
            }
        }
        return null;
    }
}
