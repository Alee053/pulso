package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.catalogs")
public record CatalogProperties(
        int maxItemsPerCatalog) {

    public CatalogProperties {
        if (maxItemsPerCatalog < 1) {
            throw new IllegalArgumentException(
                    "pulso.catalogs.max-items-per-catalog must be greater than zero.");
        }
    }
}
