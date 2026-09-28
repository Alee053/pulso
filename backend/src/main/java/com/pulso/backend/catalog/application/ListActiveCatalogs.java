package com.pulso.backend.catalog.application;

import org.springframework.stereotype.Service;

import com.pulso.backend.catalog.domain.ActiveCatalogs;
import com.pulso.backend.config.CatalogProperties;

@Service
public class ListActiveCatalogs {

    private final ActiveCatalogRepository repository;
    private final CatalogProperties properties;

    public ListActiveCatalogs(
            ActiveCatalogRepository repository,
            CatalogProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public ActiveCatalogs execute() {
        return new ActiveCatalogs(
                repository.findActiveSectors(properties.maxItemsPerCatalog()),
                repository.findActiveTags(properties.maxItemsPerCatalog()),
                repository.findActiveSkills(properties.maxItemsPerCatalog()));
    }
}
