package com.pulso.backend.project.api;

import com.pulso.backend.project.domain.PublicProjectDetail;

public record PublicProjectResponse(
        PublicProjectDetail data) {

    public static PublicProjectResponse from(PublicProjectDetail project) {
        return new PublicProjectResponse(project);
    }
}
