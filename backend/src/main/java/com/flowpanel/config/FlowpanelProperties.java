package com.flowpanel.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowpanel")
public record FlowpanelProperties(List<String> corsOrigins, Jwt jwt) {

    public record Jwt(String secret, Duration ttl, String cookieName, boolean secureCookie) {
    }
}
