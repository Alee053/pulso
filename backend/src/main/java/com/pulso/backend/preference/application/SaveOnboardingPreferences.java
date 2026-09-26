package com.pulso.backend.preference.application;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.catalog.application.ActiveSectorLookup;
import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.PreferenceProperties;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;

@Service
public class SaveOnboardingPreferences {

    private final OnboardingPreferencesRepository repository;
    private final ActiveSectorLookup activeSectorLookup;
    private final PreferenceProperties properties;

    public SaveOnboardingPreferences(
            OnboardingPreferencesRepository repository,
            ActiveSectorLookup activeSectorLookup,
            PreferenceProperties properties) {
        this.repository = repository;
        this.activeSectorLookup = activeSectorLookup;
        this.properties = properties;
    }

    public OnboardingPreferences.Limits limits() {
        return new OnboardingPreferences.Limits(
                properties.maxItemsPerField(),
                properties.maxTokenLength(),
                properties.sectorIdMaxLength());
    }

    @Transactional
    public OnboardingPreferences execute(
            UUID authenticatedProfileId,
            OnboardingPreferences preferences) {
        if (authenticatedProfileId == null) {
            throw new OnboardingPreferencesValidationException(
                    ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
        if (preferences == null) {
            throw new OnboardingPreferencesValidationException(ApiMessages.PREFERENCES_REQUIRED);
        }
        if (!authenticatedProfileId.equals(preferences.profileId())) {
            throw new OnboardingPreferencesValidationException(
                    ApiMessages.PREFERENCES_WRONG_PROFILE);
        }
        Set<UUID> selectedSectorIds = new HashSet<>();
        for (String sector : preferences.preferredSectors()) {
            Optional<UUID> activeSectorId = activeSectorLookup.findActiveSectorId(sector);
            if (activeSectorId.isEmpty()) {
                throw new OnboardingPreferencesValidationException(
                        String.format(ApiMessages.SECTOR_UNKNOWN, sector));
            }
            if (!selectedSectorIds.add(activeSectorId.get())) {
                throw new OnboardingPreferencesValidationException(
                        String.format(ApiMessages.SECTOR_DUPLICATE, sector));
            }
        }
        return repository.upsert(preferences);
    }
}
