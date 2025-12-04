package com.netfliz.encoder.entity.converter;

import com.netfliz.encoder.entity.enums.ProcessLogsObjectType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = true)
public class ProcessLogsObjectTypeConverter implements AttributeConverter<ProcessLogsObjectType, Integer> {
    @Override
    public Integer convertToDatabaseColumn(ProcessLogsObjectType processLogsObjectType) {
        return Objects.isNull(processLogsObjectType) ? null : processLogsObjectType.getId();
    }

    @Override
    public ProcessLogsObjectType convertToEntityAttribute(Integer integer) {
        return ProcessLogsObjectType.fromId(integer);
    }
}
