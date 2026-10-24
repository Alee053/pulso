package com.pulso.backend.matching.domain;

import java.util.Set;
import java.util.UUID;

/**
 * The person-side data the MVP can compare. Every collection may be empty when
 * the person has not completed onboarding.
 *
 * @param preferredSectors sector ids or slugs exactly as onboarding stored them
 */
public record MatchProfile(
        boolean onboardingCompleted,
        Set<String> intentions,
        Set<String> preferredSectors,
        Set<String> preferredStages,
        Set<UUID> tagIds,
        Set<UUID> skillIds,
        String city,
        String department) {

    public MatchProfile {
        intentions = intentions == null ? Set.of() : Set.copyOf(intentions);
        preferredSectors = preferredSectors == null ? Set.of() : Set.copyOf(preferredSectors);
        preferredStages = preferredStages == null ? Set.of() : Set.copyOf(preferredStages);
        tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
        skillIds = skillIds == null ? Set.of() : Set.copyOf(skillIds);
    }
}
