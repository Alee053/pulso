package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.connection-inbox")
public record ConnectionInboxProperties(int defaultLimit, int maxLimit, int maxOffset) {
}
