package com.pulso.backend.opportunity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateProjectOpportunityRequest(
        String opportunityType,
        String title,
        String description,
        String city,
        String department,
        Boolean remoteAllowed,
        String status) {

    @JsonAnySetter
    public void rejectProtectedOrUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(String.format(ApiMessages.FIELD_NOT_ACCEPTED, fieldName));
    }
}
