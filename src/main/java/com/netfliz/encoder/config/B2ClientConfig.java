package com.netfliz.encoder.config;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.netfliz.encoder.constant.B2Properties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;;

@Slf4j
@Configuration
@AllArgsConstructor
public class B2ClientConfig {
    private final B2Properties b2Properties;

    @Bean(destroyMethod = "close")
    public B2StorageClient b2StorageClient() {

        try {
            log.info("Initializing B2 Storage Client...");

            B2StorageClient client = B2StorageClientFactory
                    .createDefaultFactory()
                    .create(b2Properties.getApplicationKeyId(), b2Properties.getApplicationKey(), "netfliz-encoder/1.0");

            log.info("B2 Storage Client initialized successfully");

            return client;

        } catch (Exception e) {
            log.error("Failed to initialize B2 Storage Client", e);
            throw new RuntimeException("B2 initialization failed", e);
        }
    }
}