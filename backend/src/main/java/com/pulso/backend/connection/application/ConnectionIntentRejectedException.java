package com.pulso.backend.connection.application;

public final class ConnectionIntentRejectedException extends RuntimeException {

    public enum Reason {
        PROFILE_INACTIVE,
        CONTEXT_UNAVAILABLE,
        DUPLICATE_PENDING
    }

    private final Reason reason;

    public ConnectionIntentRejectedException(Reason reason) {
        super("Connection intent rejected: " + reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
