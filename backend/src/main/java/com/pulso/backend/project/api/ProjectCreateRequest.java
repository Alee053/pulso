package com.pulso.backend.project.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.project.domain.ProjectDraft;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ProjectCreateRequest(
        String slug,
        String name,
        String tagline,
        String description,
        UUID sectorId,
        String city,
        String stage) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Field '" + fieldName + "' is not accepted.");
    }

    public ProjectDraft toDraft() {
        return new ProjectDraft(
                slug,
                name,
                tagline,
                description,
                sectorId,
                city,
                stage);
    }
}
