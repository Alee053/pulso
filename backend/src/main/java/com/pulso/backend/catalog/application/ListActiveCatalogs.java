package com.pulso.backend.catalog.application;

import org.springframework.stereotype.Service;

import com.pulso.backend.catalog.domain.ActiveCatalogs;

@Service
public class ListActiveCatalogs {

    static final int MAX_ITEMS_PER_CATALOG = 500;

    private final ActiveCatalogRepository repository;

    public ListActiveCatalogs(ActiveCatalogRepository repository) {
        this.repository = repository;
    }

    public ActiveCatalogs execute() {
        return new ActiveCatalogs(
                repository.findActiveSectors(MAX_ITEMS_PER_CATALOG),
                repository.findActiveTags(MAX_ITEMS_PER_CATALOG),
                repository.findActiveSkills(MAX_ITEMS_PER_CATALOG));
    }
}
