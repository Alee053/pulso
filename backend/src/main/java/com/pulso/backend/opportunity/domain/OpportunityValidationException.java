package com.pulso.backend.opportunity.domain;

public class OpportunityValidationException extends RuntimeException {

    public OpportunityValidationException(String message) {
        super(message);
    }
}
