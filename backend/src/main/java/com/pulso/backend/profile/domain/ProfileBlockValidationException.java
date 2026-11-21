package com.pulso.backend.profile.domain;

public class ProfileBlockValidationException extends RuntimeException {
    public ProfileBlockValidationException(String message) {
        super(message);
    }
}