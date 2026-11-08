package com.pulso.backend.messaging.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pulso.backend.config.ApiMessages;

/**
 * Only the text of the message is client-controlled. The sender, type, status
 * and timestamps are derived by the backend, so any other field is rejected.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SendMessageRequest(String body) {

    @JsonAnySetter
    public void rejectProtectedOrUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(String.format(ApiMessages.FIELD_NOT_ACCEPTED, fieldName));
    }
}
