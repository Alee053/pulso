package com.pulso.backend.preference.domain;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.pulso.backend.config.ApiMessages;

public record ProfileSkill(String skillId, String proficiency) {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public ProfileSkill {
        if (skillId == null || !UUID_PATTERN.matcher(skillId.trim()).matches()) {
            throw new OnboardingPreferencesValidationException(
                    String.format(ApiMessages.PREFERENCES_CATALOG_ID_INVALID, "skills"));
        }
        skillId = UUID.fromString(skillId.trim()).toString();
        if (proficiency != null) {
            proficiency = proficiency.trim().toUpperCase(Locale.ROOT);
            if (!OnboardingOptions.PROFICIENCIES.contains(proficiency)) {
                throw new OnboardingPreferencesValidationException(ApiMessages.SKILL_PROFICIENCY_INVALID);
            }
        }
    }
}
