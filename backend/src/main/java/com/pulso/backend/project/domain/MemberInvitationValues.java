package com.pulso.backend.project.domain;

import java.util.Locale;
import java.util.Set;

final class MemberInvitationValues {

    private MemberInvitationValues() {
    }

    static String requireKnown(String value, String field, Set<String> allowedValues) {
        if (value == null || value.trim().isEmpty()) {
            throw new ProjectValidationException(field + " is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ProjectValidationException(
                    field + " contains unknown value '" + normalized + "'.");
        }
        return normalized;
    }
}
