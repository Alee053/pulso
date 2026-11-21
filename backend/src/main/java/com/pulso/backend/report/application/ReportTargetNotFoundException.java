package com.pulso.backend.report.application;

public class ReportTargetNotFoundException extends RuntimeException {
    public ReportTargetNotFoundException() {
        super("Report target is unavailable.");
    }
}
