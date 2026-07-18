package com.vetspace.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recaptcha")
@Getter
@Setter
public class RecaptchaProperties {
    private String secret;
    private boolean enabled;
}
