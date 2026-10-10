package com.pulso.backend.preference.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.ProfileSkill;

public record OnboardingPreferencesResponse(
        UUID profileId,
        List<String> intentions,
        List<String> preferredSectors,
        List<String> preferredStages,
        List<String> preferredTags,
        List<String> preferredSkills,
        Map<String, Double> recommendationWeights,
        List<SkillResponse> skills,
        OffsetDateTime onboardingCompletedAt) {

    static OnboardingPreferencesResponse from(OnboardingPreferences preferences) {
        return new OnboardingPreferencesResponse(
                preferences.profileId(),
                preferences.intentions(),
                preferences.preferredSectors(),
                preferences.preferredStages(),
                preferences.preferredTags(),
                preferences.preferredSkills(),
                preferences.recommendationWeights(),
                preferences.skills().stream().map(SkillResponse::from).toList(),
                preferences.onboardingCompletedAt());
    }

    public record SkillResponse(String skillId, String proficiency) {
        static SkillResponse from(ProfileSkill skill) {
            return new SkillResponse(skill.skillId(), skill.proficiency());
        }
    }
}
