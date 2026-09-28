package com.pulso.backend.project.domain;

import java.util.Locale;
import java.util.Set;

import com.pulso.backend.config.ApiMessages;

final class MemberInvitationValues {

    private MemberInvitationValues() {
    }

    static String requireKnown(String value, String field, Set<String> allowedValues) {
        if (value == null || value.trim().isEmpty()) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.KNOWN_VALUE_REQUIRED, field));
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.KNOWN_VALUE_UNKNOWN, field, normalized));
        }
        return normalized;
    }
}
