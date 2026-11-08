package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.messages")
public record MessageProperties(
        int defaultOffset,
        int defaultLimit,
        int maxLimit,
        int maxOffset,
        int bodyMaxLength) {
}
