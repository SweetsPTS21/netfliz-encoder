package com.netfliz.encoder.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserType {
    COMMON(0, "COMMON"),
    VIP_ONE(1, "VIP_ONE"),
    VIP_TWO(2, "VIP_TWO"),
    VIP_THREE(3, "VIP_THREE"),
    SUPER_VIP(4, "SUPER_VIP");

    private final Integer id;
    private final String name;

    public static UserType fromId(Integer value) {
        for (UserType userType : UserType.values()) {
            if (userType.getId().equals(value)) {
                return userType;
            }
        }
        throw new IllegalArgumentException("Invalid UserType value: " + value);
    }
}
