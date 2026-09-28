package com.pulso.backend.project.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.project.domain.ProjectUpdatePatch;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ProjectUpdateRequest(
        String name,
        String tagline,
        String description,
        String valueProposition,
        String stage,
        UUID sectorId,
        String city,
        String department) {

    @JsonAnySetter
    public void rejectProtectedOrUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(
                String.format(ApiMessages.FIELD_NOT_ACCEPTED, fieldName));
    }

    public ProjectUpdatePatch toPatch(ProjectUpdatePatch.Limits limits) {
        return new ProjectUpdatePatch(
                name,
                tagline,
                description,
                valueProposition,
                stage,
                sectorId,
                city,
                department,
                limits);
    }
}
