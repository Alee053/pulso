package com.pulso.backend.profile.application;

import com.pulso.backend.config.ApiMessages;

public class InvalidAuthenticatedSubjectException extends RuntimeException {

    public InvalidAuthenticatedSubjectException() {
        super(ApiMessages.UNAUTHORIZED_DETAIL);
    }
}
