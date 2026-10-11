package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.project-opportunities")
public record ProjectOpportunityProperties(
        int defaultOffset,
        int defaultLimit,
        int maxLimit,
        int maxOffset) {
}
