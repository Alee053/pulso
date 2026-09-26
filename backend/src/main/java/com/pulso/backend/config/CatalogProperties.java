package com.pulso.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulso.catalogs")
public record CatalogProperties(
        int maxItemsPerCatalog) {
}
