package com.pulso.backend.project.api;

import java.util.UUID;

import com.pulso.backend.project.domain.ProjectShareLink;

public record ProjectShareResponse(
        UUID projectId,
        String slug,
        String url) {

    public static ProjectShareResponse from(ProjectShareLink link) {
        return new ProjectShareResponse(link.projectId(), link.slug(), link.url());
    }
}