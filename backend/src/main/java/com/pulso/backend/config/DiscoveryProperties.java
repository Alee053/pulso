package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.discovery")
public record DiscoveryProperties(
        int defaultOffset,
        int defaultLimit,
        int maxLimit,
        int maxOffset) {
}
