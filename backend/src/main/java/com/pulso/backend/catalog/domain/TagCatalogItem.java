package com.pulso.backend.catalog.domain;

import java.util.UUID;

public record TagCatalogItem(
        UUID id,
        String slug,
        String name,
        String type) {
}