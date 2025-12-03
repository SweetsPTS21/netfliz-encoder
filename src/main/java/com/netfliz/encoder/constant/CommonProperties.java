package com.netfliz.encoder.constant;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Data
@EqualsAndHashCode
@Configuration
@ConfigurationProperties(prefix = "spring.cors")
public class CommonProperties {
    private Set<String> allowedOriginPatterns = Set.of("http://localhost:*,https://*.swpts.site,https://*.up.railway.app");
}
