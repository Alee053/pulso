package com.pulso.backend.preference.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.preference.domain.OnboardingPreferences;

@JsonIgnoreProperties(ignoreUnknown = false)
public record OnboardingPreferencesRequest(
        List<String> intentions,
        List<String> preferredSectors,
        List<String> preferredStages) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(
                String.format(ApiMessages.FIELD_NOT_ACCEPTED, fieldName));
    }

    public OnboardingPreferences toPreferences(
            UUID profileId,
            OnboardingPreferences.Limits limits) {
        return new OnboardingPreferences(
                profileId,
                intentions,
                preferredSectors == null ? List.of() : preferredSectors,
                preferredStages,
                limits);
    }
}
