package com.pulso.backend.discovery.application;

import java.util.List;

import com.pulso.backend.discovery.domain.StartupSummary;

public interface StartupCatalog {

    List<StartupSummary> findPublished(int offset, int limit);
}
