package com.pulso.backend.project.domain;

import com.pulso.backend.config.ApiMessages;

public final class ProjectReviewException extends RuntimeException {
    public enum Reason { FORBIDDEN, OWN_PROJECT, NOT_FOUND, STATE_CHANGED, UNAVAILABLE }

    private final Reason reason;

    public ProjectReviewException(Reason reason) {
        super(switch (reason) {
            case FORBIDDEN -> ApiMessages.PROJECT_REVIEW_FORBIDDEN;
            case OWN_PROJECT -> ApiMessages.PROJECT_REVIEW_OWN_PROJECT;
            case NOT_FOUND -> ApiMessages.PROJECT_REVIEW_NOT_FOUND;
            case STATE_CHANGED -> ApiMessages.PROJECT_REVIEW_STATE_CHANGED;
            case UNAVAILABLE -> ApiMessages.PROJECT_REVIEW_UNAVAILABLE;
        });
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
