package com.pulso.backend.catalog.application;

import java.util.List;

import com.pulso.backend.catalog.domain.SectorCatalogItem;
import com.pulso.backend.catalog.domain.SkillCatalogItem;
import com.pulso.backend.catalog.domain.TagCatalogItem;

public interface ActiveCatalogRepository {

    List<SectorCatalogItem> findActiveSectors(int limit);

    List<TagCatalogItem> findActiveTags(int limit);

    List<SkillCatalogItem> findActiveSkills(int limit);
}