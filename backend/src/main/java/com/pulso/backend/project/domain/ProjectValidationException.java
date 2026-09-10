package com.pulso.backend.project.domain;

public class ProjectValidationException extends RuntimeException {

    public ProjectValidationException(String message) {
        super(message);
    }
}
