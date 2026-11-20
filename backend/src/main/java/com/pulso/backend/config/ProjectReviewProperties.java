package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.project-review")
public record ProjectReviewProperties(
        int defaultOffset, int defaultLimit, int maxLimit, int maxOffset, int noteMaxLength) {
}
