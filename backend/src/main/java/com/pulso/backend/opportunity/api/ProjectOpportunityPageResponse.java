package com.pulso.backend.opportunity.api;

import java.util.List;

import com.pulso.backend.opportunity.application.ProjectOpportunityPage;

public record ProjectOpportunityPageResponse(
        List<ProjectOpportunityResponse> data,
        Meta meta) {

    public static ProjectOpportunityPageResponse from(ProjectOpportunityPage page) {
        return new ProjectOpportunityPageResponse(
                page.data().stream().map(ProjectOpportunityResponse::from).toList(),
                new Meta(page.offset(), page.limit(), page.hasMore()));
    }

    public record Meta(int offset, int limit, boolean hasMore) {
    }
}
