package com.pulso.backend.catalog.domain;

import java.util.List;

public record ActiveCatalogs(
        List<SectorCatalogItem> sectors,
        List<TagCatalogItem> tags,
        List<SkillCatalogItem> skills) {
}
