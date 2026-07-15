package com.vetspace.config;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActuatorConfiguration {
    public ActuatorConfiguration(WebEndpointProperties properties) {
        properties.getExposure().getInclude().clear();
        properties.getExposure().getInclude().add("health");
    }
}
