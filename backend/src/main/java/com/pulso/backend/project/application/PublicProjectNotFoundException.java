package com.pulso.backend.project.application;

public class PublicProjectNotFoundException extends RuntimeException {

    public PublicProjectNotFoundException() {
        super("The project does not exist or is not publicly available.");
    }
}
