package com.pulso.backend.matching.domain;

import java.util.List;
import java.util.UUID;

/** An open opportunity together with the public project data it is compared on. */
public record MatchOpportunity(
        UUID id,
        String title,
        String opportunityType,
        String city,
        String department,
        boolean remoteAllowed,
        List<Skill> skills,
        Sector sector,
        String stage,
        List<Tag> tags) {

    public MatchOpportunity {
        skills = skills == null ? List.of() : List.copyOf(skills);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public record Skill(UUID id, String name, boolean required) {
    }

    public record Sector(UUID id, String slug, String name) {
    }

    public record Tag(UUID id, String name) {
    }
}
