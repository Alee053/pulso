package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.notifications")
public record NotificationProperties(
        int defaultOffset,
        int defaultLimit,
        int maxLimit,
        int maxOffset) {
}
