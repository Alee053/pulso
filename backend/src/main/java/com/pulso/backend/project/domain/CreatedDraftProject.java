package com.pulso.backend.project.domain;

public record CreatedDraftProject(
        Project project,
        ProjectMember owner) {
}
