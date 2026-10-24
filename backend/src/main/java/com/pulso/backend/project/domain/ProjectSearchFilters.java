package com.pulso.backend.project.domain;

import java.util.List;

public record ProjectSearchFilters(
        String query,
        String sector,
        String stage,
        String location,
        List<String> tags,
        Boolean hasOpenOpportunities
) {
}
