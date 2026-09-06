package com.pulso.backend.catalog.domain;

import java.util.UUID;

public record SectorCatalogItem(
        UUID id,
        String slug,
        String name,
        String description) {
}
