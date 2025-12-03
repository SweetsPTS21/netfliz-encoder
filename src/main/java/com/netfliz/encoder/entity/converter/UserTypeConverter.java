package com.netfliz.encoder.entity.converter;

import com.netfliz.encoder.entity.enums.UserType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = true)
public class UserTypeConverter implements AttributeConverter<UserType, Integer> {
    @Override
    public Integer convertToDatabaseColumn(UserType UserType) {
        return Objects.isNull(UserType) ? null : UserType.getId();
    }

    @Override
    public UserType convertToEntityAttribute(Integer integer) {
        return Objects.isNull(integer) ? null : UserType.fromId(integer);
    }
}
