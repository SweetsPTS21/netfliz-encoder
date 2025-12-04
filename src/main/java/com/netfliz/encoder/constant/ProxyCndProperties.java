package com.netfliz.encoder.constant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "proxy.cnd")
public class ProxyCndProperties {
    private String streamUrl = "";
}
