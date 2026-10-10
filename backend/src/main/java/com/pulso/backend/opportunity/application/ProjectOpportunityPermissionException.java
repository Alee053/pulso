package com.pulso.backend.opportunity.application;

import com.pulso.backend.config.ApiMessages;

public class ProjectOpportunityPermissionException extends RuntimeException {

    public ProjectOpportunityPermissionException() {
        super(ApiMessages.MEMBERSHIP_PERMISSION_DETAIL);
    }
}
