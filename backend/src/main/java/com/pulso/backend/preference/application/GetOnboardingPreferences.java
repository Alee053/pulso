package com.pulso.backend.preference.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;

@Service
public class GetOnboardingPreferences {

    private final OnboardingPreferencesRepository repository;

    public GetOnboardingPreferences(OnboardingPreferencesRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public OnboardingPreferences execute(UUID authenticatedProfileId) {
        requireAuthenticatedProfileId(authenticatedProfileId);
        return repository.findByProfileId(authenticatedProfileId)
                .orElseGet(() -> OnboardingPreferences.empty(authenticatedProfileId));
    }

    private static void requireAuthenticatedProfileId(UUID profileId) {
        if (profileId == null) {
            throw new OnboardingPreferencesValidationException(
                    "authenticatedProfileId is required.");
        }
    }
}
