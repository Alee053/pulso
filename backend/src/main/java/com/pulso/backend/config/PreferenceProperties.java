package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.preferences")
public record PreferenceProperties(
        int maxItemsPerField,
        int maxTokenLength,
        int sectorIdMaxLength) {
}
