package com.pulso.backend.project.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = false)
public record MemberInvitationCreateRequest(String inviteeEmail) {
    @JsonAnySetter public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Field '" + name + "' is not accepted.");
    }
}