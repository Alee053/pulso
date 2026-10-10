package com.pulso.backend.opportunity.application;

public class ProjectOpportunityConflictException extends RuntimeException {

    public ProjectOpportunityConflictException() {
        super("the opportunity changed before the action completed.");
    }
}
