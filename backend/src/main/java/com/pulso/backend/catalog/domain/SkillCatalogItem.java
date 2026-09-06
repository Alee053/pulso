package com.pulso.backend.catalog.domain;

import java.util.UUID;

public record SkillCatalogItem(
        UUID id,
        String slug,
        String name,
        String category) {
}
