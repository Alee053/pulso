package com.pulso.backend.preference.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;
import com.pulso.backend.preference.domain.ProfileSkill;

@JsonIgnoreProperties(ignoreUnknown = false)
public record OnboardingPreferencesRequest(
        List<String> intentions,
        List<String> preferredSectors,
        List<String> preferredStages,
        List<String> preferredTags,
        List<String> preferredSkills,
        List<SkillRequest> skills,
        Boolean completeOnboarding) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(
                String.format(ApiMessages.FIELD_NOT_ACCEPTED, fieldName));
    }

    public OnboardingPreferences toPreferences(
            UUID profileId,
            OnboardingPreferences.Limits limits) {
        if (skills != null && preferredSkills != null) {
            throw new OnboardingPreferencesValidationException(ApiMessages.SKILL_SELECTION_AMBIGUOUS);
        }
        List<ProfileSkill> selections = skills == null
                ? (preferredSkills == null ? List.<String>of() : preferredSkills).stream()
                        .map(id -> new ProfileSkill(id, null)).toList()
                : skills.stream().map(selection -> {
                    if (selection == null) {
                        throw new OnboardingPreferencesValidationException(ApiMessages.SKILL_SELECTION_REQUIRED);
                    }
                    if (selection.proficiency() == null) {
                        throw new OnboardingPreferencesValidationException(ApiMessages.SKILL_PROFICIENCY_REQUIRED);
                    }
                    return new ProfileSkill(selection.skillId(), selection.proficiency());
                }).toList();
        return new OnboardingPreferences(
                profileId,
                intentions,
                preferredSectors == null ? List.of() : preferredSectors,
                preferredStages,
                preferredTags == null ? List.of() : preferredTags,
                selections,
                java.util.Map.of(),
                null,
                limits);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SkillRequest(String skillId, String proficiency) {

        @JsonAnySetter
        public void rejectUnknownField(String fieldName, Object value) {
            throw new IllegalArgumentException(String.format(ApiMessages.FIELD_NOT_ACCEPTED, fieldName));
        }
    }
}
