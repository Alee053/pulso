package com.pulso.backend.preference.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.preference.domain.OnboardingPreferences;

@JsonIgnoreProperties(ignoreUnknown = false)
public record OnboardingPreferencesRequest(
        List<String> intentions,
        List<String> preferredSectors,
        List<String> preferredStages) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Field '" + fieldName + "' is not accepted.");
    }

    public OnboardingPreferences toPreferences(UUID profileId) {
        return new OnboardingPreferences(
                profileId,
                intentions,
                preferredSectors == null ? List.of() : preferredSectors,
                preferredStages);
    }
}
