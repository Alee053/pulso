package com.pulso.backend.ops.logging;

import java.util.Objects;

/** Identifies the application operation associated with a diagnostic event. */
public record LogContext(
        String module,
        String operation,
        String requestId,
        String userId) {

    public LogContext {
        module = requireValue(module, "module");
        operation = requireValue(operation, "operation");
    }

    private static String requireValue(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
