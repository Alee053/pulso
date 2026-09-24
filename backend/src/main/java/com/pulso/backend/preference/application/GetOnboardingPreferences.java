package com.pulso.backend.preference.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.PreferenceProperties;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;

@Service
public class GetOnboardingPreferences {

    private final OnboardingPreferencesRepository repository;
    private final PreferenceProperties properties;

    public GetOnboardingPreferences(
            OnboardingPreferencesRepository repository,
            PreferenceProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public OnboardingPreferences execute(UUID authenticatedProfileId) {
        requireAuthenticatedProfileId(authenticatedProfileId);
        return repository.findByProfileId(authenticatedProfileId)
                .orElseGet(() -> OnboardingPreferences.empty(
                        authenticatedProfileId,
                        limits()));
    }

    public OnboardingPreferences.Limits limits() {
        return new OnboardingPreferences.Limits(
                properties.maxItemsPerField(),
                properties.maxTokenLength(),
                properties.sectorIdMaxLength());
    }

    private static void requireAuthenticatedProfileId(UUID profileId) {
        if (profileId == null) {
            throw new OnboardingPreferencesValidationException(
                    ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
    }
}
