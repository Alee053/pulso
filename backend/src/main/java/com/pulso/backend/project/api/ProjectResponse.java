package com.pulso.backend.project.api;

import java.util.UUID;

import com.pulso.backend.project.domain.CreatedDraftProject;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectMember;

public record ProjectResponse(
        ProjectData project,
        OwnerData owner) {

    public static ProjectResponse from(CreatedDraftProject createdProject) {
        return new ProjectResponse(
                ProjectData.from(createdProject.project()),
                OwnerData.from(createdProject.owner()));
    }

    public record ProjectData(
            UUID id,
            String slug,
            String name,
            String tagline,
            String description,
            UUID sectorId,
            String city,
            String stage,
            String status,
            UUID createdByProfileId) {

        private static ProjectData from(Project project) {
            return new ProjectData(
                    project.id(),
                    project.slug(),
                    project.name(),
                    project.tagline(),
                    project.description(),
                    project.sectorId(),
                    project.city(),
                    project.stage(),
                    project.status(),
                    project.createdByProfileId());
        }
    }

    public record OwnerData(
            UUID id,
            UUID projectId,
            UUID profileId,
            String role,
            String membershipStatus) {

        private static OwnerData from(ProjectMember owner) {
            return new OwnerData(
                    owner.id(),
                    owner.projectId(),
                    owner.profileId(),
                    owner.role(),
                    owner.membershipStatus());
        }
    }
}
