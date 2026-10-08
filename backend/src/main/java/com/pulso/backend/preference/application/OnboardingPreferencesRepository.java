package com.pulso.backend.preference.application;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import com.pulso.backend.preference.domain.OnboardingPreferences;

public interface OnboardingPreferencesRepository {

    Optional<OnboardingPreferences> findByProfileId(UUID profileId);

    OnboardingPreferences upsert(OnboardingPreferences preferences);

    void saveRecommendationWeights(UUID profileId, Map<String, Double> weights);
}
