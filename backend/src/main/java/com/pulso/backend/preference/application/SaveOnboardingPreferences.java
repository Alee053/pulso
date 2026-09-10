package com.pulso.backend.preference.application;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.catalog.application.ActiveSectorLookup;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;

@Service
public class SaveOnboardingPreferences {

    private final OnboardingPreferencesRepository repository;
    private final ActiveSectorLookup activeSectorLookup;

    public SaveOnboardingPreferences(
            OnboardingPreferencesRepository repository,
            ActiveSectorLookup activeSectorLookup) {
        this.repository = repository;
        this.activeSectorLookup = activeSectorLookup;
    }

    @Transactional
    public OnboardingPreferences execute(
            UUID authenticatedProfileId,
            OnboardingPreferences preferences) {
        if (authenticatedProfileId == null) {
            throw new OnboardingPreferencesValidationException(
                    "authenticatedProfileId is required.");
        }
        if (preferences == null) {
            throw new OnboardingPreferencesValidationException("preferences are required.");
        }
        if (!authenticatedProfileId.equals(preferences.profileId())) {
            throw new OnboardingPreferencesValidationException(
                    "preferences do not belong to the authenticated profile.");
        }
        Set<UUID> selectedSectorIds = new HashSet<>();
        for (String sector : preferences.preferredSectors()) {
            Optional<UUID> activeSectorId = activeSectorLookup.findActiveSectorId(sector);
            if (activeSectorId.isEmpty()) {
                throw new OnboardingPreferencesValidationException(
                        "preferredSectors contains unknown or inactive sector '" + sector + "'.");
            }
            if (!selectedSectorIds.add(activeSectorId.get())) {
                throw new OnboardingPreferencesValidationException(
                        "preferredSectors must not contain duplicates: '" + sector + "'.");
            }
        }
        return repository.upsert(preferences);
    }
}
