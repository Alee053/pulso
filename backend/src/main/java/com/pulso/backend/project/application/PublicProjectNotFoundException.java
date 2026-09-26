package com.pulso.backend.project.application;

import com.pulso.backend.config.ApiMessages;

public class PublicProjectNotFoundException extends RuntimeException {

    public PublicProjectNotFoundException() {
        super(ApiMessages.PUBLIC_PROJECT_NOT_FOUND_DETAIL);
    }
}
