package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.profile")
public record ProfileProperties(
        int displayNameMaxLength,
        int avatarPathMaxLength,
        int shortBioMaxLength,
        int cityMaxLength,
        int departmentMaxLength) {
}
