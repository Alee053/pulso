package com.pulso.backend.opportunity.application;

public class ProjectOpportunityNotFoundException extends RuntimeException {

    public ProjectOpportunityNotFoundException() {
        super("opportunity does not exist in this project.");
    }
}
