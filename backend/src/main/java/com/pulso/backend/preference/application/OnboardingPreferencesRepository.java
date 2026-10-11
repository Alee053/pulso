package com.pulso.backend.preference.application;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import com.pulso.backend.preference.domain.OnboardingPreferences;

public interface OnboardingPreferencesRepository {

    Optional<OnboardingPreferences> findByProfileId(UUID profileId);

    default OnboardingPreferences upsert(OnboardingPreferences preferences) {
        return upsert(preferences, false);
    }

    OnboardingPreferences upsert(OnboardingPreferences preferences, boolean completeOnboarding);

    void saveRecommendationWeights(UUID profileId, Map<String, Double> weights);
}
