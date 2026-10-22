package com.pulso.backend.opportunity.application;

public class OpportunitySkillConflictException extends RuntimeException {

    public OpportunitySkillConflictException() {
        super("skill is already associated with this opportunity.");
    }
}
