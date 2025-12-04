package com.netfliz.encoder.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.netfliz.encoder.entity.converter.ProcessLogsObjectTypeConverter;
import com.netfliz.encoder.entity.converter.ProcessLogsStatusConverter;
import com.netfliz.encoder.entity.enums.ProcessLogsObjectType;
import com.netfliz.encoder.entity.enums.ProcessLogsStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;

@Data
@Entity
@Table(name = "movie_process_logs")
public class MovieProcessLogsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "object_type")
    @Convert(converter = ProcessLogsObjectTypeConverter.class)
    private ProcessLogsObjectType objectType;

    @Column(name = "status")
    @Convert(converter = ProcessLogsStatusConverter.class)
    private ProcessLogsStatus status;

    @Column(name = "progress")
    private Integer progress;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode configs;

    @Column(name = "created_at", insertable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Date createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Date updatedAt;
}
