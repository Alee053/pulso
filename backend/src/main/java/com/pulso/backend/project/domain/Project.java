package com.pulso.backend.project.domain;

import java.util.UUID;

public record Project(
        UUID id,
        String slug,
        String name,
        String tagline,
        String description,
        UUID sectorId,
        String city,
        String stage,
        String status,
        UUID createdByProfileId) {
}
