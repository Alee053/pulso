package com.pulso.backend.project.application;

public class ProjectConflictException extends RuntimeException {

    public ProjectConflictException() {
        super("A project with the same slug already exists.");
    }
}
