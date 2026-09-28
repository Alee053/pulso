package com.pulso.backend.project.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.pulso.backend.project.domain.UpdatedProject;

public record ProjectUpdateResponse(
        ProjectData project) {

    public static ProjectUpdateResponse from(UpdatedProject project) {
        return new ProjectUpdateResponse(ProjectData.from(project));
    }

    public record ProjectData(
            UUID id,
            String slug,
            String name,
            String tagline,
            String description,
            String valueProposition,
            UUID sectorId,
            String stage,
            String city,
            String department,
            String status,
            String verificationStatus,
            UUID createdByProfileId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        private static ProjectData from(UpdatedProject project) {
            return new ProjectData(
                    project.id(),
                    project.slug(),
                    project.name(),
                    project.tagline(),
                    project.description(),
                    project.valueProposition(),
                    project.sectorId(),
                    project.stage(),
                    project.city(),
                    project.department(),
                    project.status(),
                    project.verificationStatus(),
                    project.createdByProfileId(),
                    project.createdAt(),
                    project.updatedAt());
        }
    }
}
