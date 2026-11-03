package com.pulso.backend.connection.domain;

import java.util.Locale;

public enum ConnectionIntentReason {
    COLLABORATE,
    WORK,
    TRY_PRODUCT,
    BUY,
    INSTITUTION,
    INVEST,
    OFFER_SERVICE,
    OTHER;

    public static ConnectionIntentReason parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ConnectionIntentValidationException("reason is required.");
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ConnectionIntentValidationException(
                    "reason must be one of COLLABORATE, WORK, TRY_PRODUCT, BUY, INSTITUTION, INVEST, OFFER_SERVICE, OTHER.");
        }
    }
}
