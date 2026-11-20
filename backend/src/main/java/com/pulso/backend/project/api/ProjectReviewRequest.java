package com.pulso.backend.project.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ProjectReviewRequest(String decision, String verificationStatus, String note) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException(String.format(ApiMessages.FIELD_NOT_ACCEPTED, name));
    }
}
