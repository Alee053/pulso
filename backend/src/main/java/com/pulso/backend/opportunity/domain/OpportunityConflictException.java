package com.pulso.backend.opportunity.domain;

public class OpportunityConflictException extends RuntimeException {

    public OpportunityConflictException(String message) {
        super(message);
    }
}
