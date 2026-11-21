package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.profile-blocks")
public record ProfileBlockProperties(int defaultLimit, int maxLimit, int maxOffset) {
}
