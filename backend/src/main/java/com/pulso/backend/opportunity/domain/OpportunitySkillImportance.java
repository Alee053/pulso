package com.pulso.backend.opportunity.domain;

import java.util.Locale;

public enum OpportunitySkillImportance {
    REQUIRED,
    PREFERRED;

    public static OpportunitySkillImportance parse(String value) {
        if (value == null || value.isBlank()) {
            throw new OpportunityValidationException("importance must be REQUIRED or PREFERRED.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new OpportunityValidationException("importance must be REQUIRED or PREFERRED.");
        }
    }
}
