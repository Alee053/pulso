package com.pulso.backend.profile.api;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.profile.domain.ProfilePatch;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ProfilePatchRequest(
        String profileKind,
        String displayName,
        String avatarStoragePath,
        String shortBio,
        String city,
        String department,
        String profileVisibility,
        OffsetDateTime onboardingCompletedAt) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(
                String.format(ApiMessages.PROFILE_NOT_EDITABLE, fieldName));
    }

    public ProfilePatch toPatch(ProfilePatch.Limits limits) {
        return new ProfilePatch(
                profileKind,
                displayName,
                avatarStoragePath,
                shortBio,
                city,
                department,
                profileVisibility,
                onboardingCompletedAt,
                limits);
    }
}
