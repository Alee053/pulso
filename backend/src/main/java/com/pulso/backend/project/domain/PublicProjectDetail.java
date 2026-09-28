package com.pulso.backend.project.domain;

import java.util.List;
import java.util.UUID;

public record PublicProjectDetail(
        UUID id,
        String slug,
        String name,
        String tagline,
        String description,
        String valueProposition,
        Sector sector,
        String stage,
        String city,
        String department,
        List<Tag> tags,
        List<ExternalLink> externalLinks,
        List<Member> members) {

    public PublicProjectDetail {
        tags = tags == null ? List.of() : List.copyOf(tags);
        externalLinks = externalLinks == null ? List.of() : List.copyOf(externalLinks);
        members = members == null ? List.of() : List.copyOf(members);
    }

    public record Sector(
            UUID id,
            String slug,
            String name) {
    }

    public record Tag(
            UUID id,
            String slug,
            String name) {
    }

    public record ExternalLink(
            String type,
            String url,
            String label) {
    }

    public record Member(
            UUID profileId,
            String displayName,
            String avatarStoragePath,
            String displayTitle,
            boolean isFeatured) {
    }
}
