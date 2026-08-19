package com.pulso.backend.discovery.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.pulso.backend.discovery.domain.StartupSummary;

@Service
public class ListPublishedStartups {

    private final StartupCatalog startupCatalog;

    public ListPublishedStartups(StartupCatalog startupCatalog) {
        this.startupCatalog = startupCatalog;
    }

    public List<StartupSummary> execute(int offset, int limit) {
        return startupCatalog.findPublished(offset, limit);
    }
}
