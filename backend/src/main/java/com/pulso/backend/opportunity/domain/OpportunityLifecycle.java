package com.pulso.backend.opportunity.domain;

import java.util.Locale;
import java.util.Set;

public final class OpportunityLifecycle {

    private static final Set<String> INITIAL_STATES = Set.of("DRAFT", "OPEN");

    private OpportunityLifecycle() {
    }

    public static String initialStatus(String requestedStatus) {
        String status = requestedStatus == null
                ? "OPEN"
                : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!INITIAL_STATES.contains(status)) {
            throw new OpportunityValidationException("status must be one of DRAFT, OPEN.");
        }
        return status;
    }

    public static String transition(String currentStatus, String action) {
        if (currentStatus == null || currentStatus.isBlank()) {
            throw new OpportunityValidationException("opportunity status is required.");
        }
        if (action == null || action.isBlank()) {
            throw new OpportunityValidationException("action is required.");
        }

        String current = currentStatus.toUpperCase(Locale.ROOT);
        String requested = action.trim().toUpperCase(Locale.ROOT);
        return switch (requested) {
            case "OPEN" -> transitionToOpen(current);
            case "PAUSE" -> requireTransition(current, Set.of("OPEN"), "PAUSED");
            case "CLOSE" -> requireTransition(current, Set.of("OPEN", "PAUSED"), "CLOSED");
            case "ARCHIVE" -> requireTransition(
                    current,
                    Set.of("DRAFT", "OPEN", "PAUSED", "FILLED", "CLOSED"),
                    "ARCHIVED");
            default -> throw new OpportunityValidationException(
                    "action must be one of OPEN, PAUSE, CLOSE, ARCHIVE.");
        };
    }

    private static String transitionToOpen(String currentStatus) {
        return requireTransition(currentStatus, Set.of("DRAFT", "PAUSED"), "OPEN");
    }

    private static String requireTransition(
            String currentStatus,
            Set<String> allowedSources,
            String nextStatus) {
        if (!allowedSources.contains(currentStatus)) {
            throw new OpportunityConflictException(
                    "cannot change opportunity from " + currentStatus + " to " + nextStatus + ".");
        }
        return nextStatus;
    }
}
