package com.pulso.backend.interaction.api;

import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.project.domain.ProjectValidationException;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ProjectFeedbackRequest(
        String feedbackType,
        Map<String, Double> recommendationWeights) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(
                String.format(ApiMessages.FIELD_NOT_ACCEPTED, fieldName));
    }

    public ProjectFeedbackType toFeedbackType() {
        if (feedbackType == null) {
            throw new ProjectValidationException("feedbackType is required.");
        }
        try {
            return ProjectFeedbackType.valueOf(
                    feedbackType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ProjectValidationException(
                    "feedbackType must be INTERESTED or DISMISSED.");
        }
    }

    public Map<String, Double> weightsOrNull() {
        return recommendationWeights;
    }
}
