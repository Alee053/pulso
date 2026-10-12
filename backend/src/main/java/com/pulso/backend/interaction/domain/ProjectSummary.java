package com.pulso.backend.interaction.domain;

import java.util.UUID;

public record ProjectSummary(
        UUID id,
        String slug,
        String name,
        String tagline,
        String sector,
        String city,
        String stage,
        String imageUrl,
        String imageAlt) {

    public ProjectSummary(UUID id, String slug, String name, String tagline,
            String sector, String city, String stage) {
        this(id, slug, name, tagline, sector, city, stage, null, null);
    }
}
