package com.pulso.backend.catalog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.catalog.application.ListActiveCatalogs;

@RestController
@RequestMapping("/api/v1/catalogs")
public class CatalogController {

    private final ListActiveCatalogs listActiveCatalogs;

    public CatalogController(ListActiveCatalogs listActiveCatalogs) {
        this.listActiveCatalogs = listActiveCatalogs;
    }

    @GetMapping
    public CatalogResponse list() {
        return CatalogResponse.from(listActiveCatalogs.execute());
    }
}
