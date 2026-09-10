package com.pulso.backend.profile.domain;

public class ProfileValidationException extends RuntimeException {

    public ProfileValidationException(String message) {
        super(message);
    }
}
