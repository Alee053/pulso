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

    public ProjectProperties {
        requirePositive("pulso.project.slug-min-length", slugMinLength);
        requirePositive("pulso.project.slug-max-length", slugMaxLength);
        requirePositive("pulso.project.name-max-length", nameMaxLength);
        requirePositive("pulso.project.tagline-max-length", taglineMaxLength);
        requirePositive("pulso.project.description-max-length", descriptionMaxLength);
        requirePositive("pulso.project.city-max-length", cityMaxLength);
        requirePositive("pulso.project.stage-token-max-length", stageTokenMaxLength);
        requirePositive("pulso.project.invitee-email-max-length", inviteeEmailMaxLength);

        if (slugMinLength > slugMaxLength) {
            throw new IllegalArgumentException(
                    "pulso.project.slug-min-length must not exceed pulso.project.slug-max-length.");
        }
    }

    private static void requirePositive(String property, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(property + " must be greater than zero.");
        }
    }
}
