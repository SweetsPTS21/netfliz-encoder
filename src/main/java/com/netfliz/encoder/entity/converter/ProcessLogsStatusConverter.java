package com.netfliz.encoder.entity.converter;

import com.netfliz.encoder.entity.enums.ProcessLogsStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = true)
public class ProcessLogsStatusConverter implements AttributeConverter<ProcessLogsStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(ProcessLogsStatus processLogsStatus) {
        return Objects.isNull(processLogsStatus) ? null : processLogsStatus.getId();
    }

    @Override
    public ProcessLogsStatus convertToEntityAttribute(Integer integer) {
        return ProcessLogsStatus.fromId(integer);
    }
}
