package com.pulso.backend.preference.api;

import java.util.List;
import java.util.UUID;

import com.pulso.backend.preference.domain.OnboardingPreferences;

public record OnboardingPreferencesResponse(
        UUID profileId,
        List<String> intentions,
        List<String> preferredSectors,
        List<String> preferredStages) {

    static OnboardingPreferencesResponse from(OnboardingPreferences preferences) {
        return new OnboardingPreferencesResponse(
                preferences.profileId(),
                preferences.intentions(),
                preferences.preferredSectors(),
                preferences.preferredStages());
    }
}
