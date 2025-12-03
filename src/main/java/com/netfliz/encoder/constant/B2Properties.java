package com.netfliz.encoder.constant;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@EqualsAndHashCode
@Configuration
@ConfigurationProperties(prefix = "backblaze.s3")
public class B2Properties {
    private String endpoint = "";
    private String region = "";
    private String bucketId = "";
    private String bucketName = "";
    private String workerSharedSecret = "";
    private String applicationKey = "";
    private String applicationKeyId = "";
}
