package com.pulso.backend.preference.application;

import java.util.HashSet;
import java.util.List;
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
    private final ActivePreferenceCatalogLookup activePreferenceCatalogLookup;
    private final PreferenceProperties properties;

    public SaveOnboardingPreferences(
            OnboardingPreferencesRepository repository,
            ActiveSectorLookup activeSectorLookup,
            ActivePreferenceCatalogLookup activePreferenceCatalogLookup,
            PreferenceProperties properties) {
        this.repository = repository;
        this.activeSectorLookup = activeSectorLookup;
        this.activePreferenceCatalogLookup = activePreferenceCatalogLookup;
        this.properties = properties;
    }

    public OnboardingPreferences.Limits limits() {
        return new OnboardingPreferences.Limits(
                properties.maxItemsPerField(),
                properties.maxTokenLength(),
                properties.sectorIdMaxLength());
    }

    @Transactional
    public OnboardingPreferences execute(UUID authenticatedProfileId, OnboardingPreferences preferences) {
        return execute(authenticatedProfileId, preferences, false);
    }

    @Transactional
    public OnboardingPreferences execute(
            UUID authenticatedProfileId,
            OnboardingPreferences preferences,
            boolean completeOnboarding) {
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
        if (completeOnboarding) {
            preferences.requireCompleteSelections();
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

        Set<UUID> selectedTagIds = toUuidSet(preferences.preferredTags());
        if (!activePreferenceCatalogLookup.findActiveTagIds(List.copyOf(selectedTagIds))
                .containsAll(selectedTagIds)) {
            throw new OnboardingPreferencesValidationException(ApiMessages.TAG_UNKNOWN);
        }

        Set<UUID> selectedSkillIds = toUuidSet(preferences.preferredSkills());
        if (!activePreferenceCatalogLookup.findActiveSkillIds(List.copyOf(selectedSkillIds))
                .containsAll(selectedSkillIds)) {
            throw new OnboardingPreferencesValidationException(ApiMessages.SKILL_UNKNOWN);
        }
        return repository.upsert(preferences, completeOnboarding);
    }

    private static Set<UUID> toUuidSet(java.util.List<String> ids) {
        Set<UUID> result = new HashSet<>();
        ids.forEach(id -> result.add(UUID.fromString(id)));
        return result;
    }
}
