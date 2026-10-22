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
        List<Member> members,
        String imageUrl,
        String imageAlt,
        List<Opportunity> opportunities) {

    public PublicProjectDetail(UUID id, String slug, String name, String tagline,
            String description, String valueProposition, Sector sector, String stage,
            String city, String department, List<Tag> tags, List<ExternalLink> externalLinks,
            List<Member> members) {
        this(id, slug, name, tagline, description, valueProposition, sector, stage,
                city, department, tags, externalLinks, members, null, null, List.of());
    }

    public PublicProjectDetail(UUID id, String slug, String name, String tagline,
            String description, String valueProposition, Sector sector, String stage,
            String city, String department, List<Tag> tags, List<ExternalLink> externalLinks,
            List<Member> members, String imageUrl, String imageAlt) {
        this(id, slug, name, tagline, description, valueProposition, sector, stage,
                city, department, tags, externalLinks, members, imageUrl, imageAlt, List.of());
    }

    public PublicProjectDetail {
        tags = tags == null ? List.of() : List.copyOf(tags);
        externalLinks = externalLinks == null ? List.of() : List.copyOf(externalLinks);
        members = members == null ? List.of() : List.copyOf(members);
        opportunities = opportunities == null ? List.of() : List.copyOf(opportunities);
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

    public record Opportunity(
            String title,
            String opportunityType,
            String description,
            String city,
            String department,
            boolean remoteAllowed,
            List<String> requiredSkills) {

        public Opportunity {
            requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
        }
    }
}
