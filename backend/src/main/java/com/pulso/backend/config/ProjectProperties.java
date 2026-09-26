package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.project")
public record ProjectProperties(
        int slugMinLength,
        int slugMaxLength,
        int nameMaxLength,
        int taglineMaxLength,
        int descriptionMaxLength,
        int cityMaxLength,
        int stageTokenMaxLength,
        int inviteeEmailMaxLength) {
}
