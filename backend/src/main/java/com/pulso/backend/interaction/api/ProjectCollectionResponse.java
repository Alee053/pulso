package com.pulso.backend.interaction.api;

import java.util.List;

import com.pulso.backend.interaction.domain.ProjectPage;
import com.pulso.backend.interaction.domain.ProjectSummary;

public record ProjectCollectionResponse(
        List<ProjectSummaryResponse> data,
        PageMetadata meta) {

    static ProjectCollectionResponse from(ProjectPage<ProjectSummary> page) {
        return new ProjectCollectionResponse(
                page.data().stream().map(ProjectSummaryResponse::from).toList(),
                new PageMetadata(page.offset(), page.limit(), page.hasMore(), page.totalCount()));
    }

    public record PageMetadata(int offset, int limit, boolean hasMore, long totalCount) {
    }
}
