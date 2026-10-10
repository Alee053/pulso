package com.pulso.backend.opportunity.domain;

import java.util.List;
import java.util.Locale;

public record OpportunityContent(
        String opportunityType,
        String title,
        String description,
        String city,
        String department,
        boolean remoteAllowed) {

    private static final List<String> TYPES = List.of(
            "TALENT", "COLLABORATION", "CUSTOMER", "BETA_TESTER", "INVESTMENT",
            "MENTOR", "SUPPLIER", "PARTNERSHIP", "OTHER");

    public static OpportunityContent validate(
            String opportunityType,
            String title,
            String description,
            String city,
            String department,
            Boolean remoteAllowed) {
        String normalizedType = required(opportunityType, "opportunityType").toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalizedType)) {
            throw new OpportunityValidationException(
                    "opportunityType must be one of " + String.join(", ", TYPES) + ".");
        }

        String normalizedTitle = required(title, "title");
        String normalizedDescription = required(description, "description");
        String normalizedCity = optional(city);
        String normalizedDepartment = optional(department);
        if (remoteAllowed == null) {
            throw new OpportunityValidationException("remoteAllowed is required.");
        }
        if (!remoteAllowed && normalizedCity == null && normalizedDepartment == null) {
            throw new OpportunityValidationException(
                    "city or department is required when remoteAllowed is false.");
        }

        return new OpportunityContent(
                normalizedType,
                normalizedTitle,
                normalizedDescription,
                normalizedCity,
                normalizedDepartment,
                remoteAllowed);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new OpportunityValidationException(field + " is required.");
        }
        return value.trim();
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
