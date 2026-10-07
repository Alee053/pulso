package com.pulso.backend.preference.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pulso.backend.preference.domain.OnboardingPreferences;

public record OnboardingPreferencesResponse(
        UUID profileId,
        List<String> intentions,
        List<String> preferredSectors,
        List<String> preferredStages,
        List<String> preferredTags,
        List<String> preferredSkills,
        Map<String, Double> recommendationWeights) {

    static OnboardingPreferencesResponse from(OnboardingPreferences preferences) {
        return new OnboardingPreferencesResponse(
                preferences.profileId(),
                preferences.intentions(),
                preferences.preferredSectors(),
                preferences.preferredStages(),
                preferences.preferredTags(),
                preferences.preferredSkills(),
                preferences.recommendationWeights());
    }
}
