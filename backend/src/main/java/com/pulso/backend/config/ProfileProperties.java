package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.profile")
public record ProfileProperties(
        int displayNameMaxLength,
        int avatarPathMaxLength,
        int shortBioMaxLength,
        int cityMaxLength,
        int departmentMaxLength) {

    public ProfileProperties {
        requirePositive("pulso.profile.display-name-max-length", displayNameMaxLength);
        requirePositive("pulso.profile.avatar-path-max-length", avatarPathMaxLength);
        requirePositive("pulso.profile.short-bio-max-length", shortBioMaxLength);
        requirePositive("pulso.profile.city-max-length", cityMaxLength);
        requirePositive("pulso.profile.department-max-length", departmentMaxLength);
    }

    private static void requirePositive(String property, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(property + " must be greater than zero.");
        }
    }
}
