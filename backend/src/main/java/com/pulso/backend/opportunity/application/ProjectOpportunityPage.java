package com.pulso.backend.opportunity.application;

import java.util.List;

import com.pulso.backend.opportunity.domain.ProjectOpportunity;

public record ProjectOpportunityPage(
        List<ProjectOpportunity> data,
        int offset,
        int limit,
        boolean hasMore) {
}
