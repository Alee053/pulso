package com.pulso.backend.profile.application;

public class InvalidAuthenticatedSubjectException extends RuntimeException {

    public InvalidAuthenticatedSubjectException() {
        super("Authenticated subject is invalid.");
    }
}
