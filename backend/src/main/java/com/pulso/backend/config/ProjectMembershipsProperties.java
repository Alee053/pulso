package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.project-memberships")
public record ProjectMembershipsProperties(
        int defaultOffset,
        int defaultLimit,
        int maxLimit,
        int maxOffset) {
}
