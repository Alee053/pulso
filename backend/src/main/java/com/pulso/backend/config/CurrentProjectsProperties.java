package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.current-projects")
public record CurrentProjectsProperties(int defaultOffset, int defaultLimit, int maxLimit, int maxOffset) {
}
