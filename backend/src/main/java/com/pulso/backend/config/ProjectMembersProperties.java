package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.project-members")
public record ProjectMembersProperties(int defaultOffset, int defaultLimit, int maxLimit, int maxOffset) {
}
