package com.pulso.backend.project.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.project.domain.ProjectUpdatePatch;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class ProjectUpdateRequest {

    private String name;
    private boolean nameProvided;
    private String tagline;
    private boolean taglineProvided;
    private String description;
    private boolean descriptionProvided;
    private String valueProposition;
    private boolean valuePropositionProvided;
    private String stage;
    private boolean stageProvided;
    private UUID sectorId;
    private boolean sectorIdProvided;
    private String city;
    private boolean cityProvided;
    private String department;
    private boolean departmentProvided;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        nameProvided = true;
        this.name = name;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        taglineProvided = true;
        this.tagline = tagline;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        descriptionProvided = true;
        this.description = description;
    }

    public String getValueProposition() {
        return valueProposition;
    }

    public void setValueProposition(String valueProposition) {
        valuePropositionProvided = true;
        this.valueProposition = valueProposition;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        stageProvided = true;
        this.stage = stage;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public void setSectorId(UUID sectorId) {
        sectorIdProvided = true;
        this.sectorId = sectorId;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        cityProvided = true;
        this.city = city;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        departmentProvided = true;
        this.department = department;
    }

    @JsonAnySetter
    public void rejectProtectedOrUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(
                String.format(ApiMessages.FIELD_NOT_ACCEPTED, fieldName));
    }

    public ProjectUpdatePatch toPatch(ProjectUpdatePatch.Limits limits) {
        return new ProjectUpdatePatch(
                name,
                nameProvided,
                tagline,
                taglineProvided,
                description,
                descriptionProvided,
                valueProposition,
                valuePropositionProvided,
                stage,
                stageProvided,
                sectorId,
                sectorIdProvided,
                city,
                cityProvided,
                department,
                departmentProvided,
                limits);
    }
}
