package com.pulso.backend.interaction.api;

import java.util.UUID;

import com.pulso.backend.interaction.domain.ProjectSummary;

public record ProjectSummaryResponse(
        UUID id,
        String slug,
        String name,
        String tagline,
        String sector,
        String city,
        String stage) {

    static ProjectSummaryResponse from(ProjectSummary project) {
        return new ProjectSummaryResponse(
                project.id(),
                project.slug(),
                project.name(),
                project.tagline(),
                project.sector(),
                project.city(),
                project.stage());
    }
}
