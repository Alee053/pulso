package com.pulso.backend.opportunity.application;

public class OpportunitySkillNotFoundException extends RuntimeException {

    public OpportunitySkillNotFoundException() {
        super("skill association does not exist in this opportunity.");
    }
}
