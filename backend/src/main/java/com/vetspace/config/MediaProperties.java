package com.vetspace.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.media")
@Getter
@Setter
public class MediaProperties {
    private String endpoint;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private String publicBaseUrl;
}
