package com.pulso.backend.report.domain;

public class ReportValidationException extends RuntimeException {
    public ReportValidationException(String message) {
        super(message);
    }
}
