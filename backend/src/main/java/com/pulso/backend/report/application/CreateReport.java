package com.pulso.backend.report.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pulso.backend.report.domain.ReportReceipt;
import com.pulso.backend.report.domain.ReportTarget;
import com.pulso.backend.report.domain.ReportValidationException;

@Service
public class CreateReport {
    private final ReportRepository repository;

    public CreateReport(ReportRepository repository) {
        this.repository = repository;
    }

    public ReportReceipt execute(UUID reporterId, ReportTarget target, String reason, String description) {
        if (reason == null || reason.isBlank()) {
            throw new ReportValidationException("A report reason is required.");
        }
        String normalizedReason = reason.strip();
        String normalizedDescription = description == null || description.isBlank() ? null : description.strip();
        validateText(normalizedReason, 120, "Reason");
        if (normalizedDescription != null) {
            validateText(normalizedDescription, 2000, "Description");
        }
        return repository.insertIfAccessible(reporterId, target, normalizedReason, normalizedDescription)
                .orElseThrow(ReportTargetNotFoundException::new);
    }

    private static void validateText(String text, int limit, String field) {
        if (text.indexOf(0) >= 0 || text.codePointCount(0, text.length()) > limit) {
            throw new ReportValidationException(field + " contains invalid text or exceeds " + limit + " characters.");
        }
    }
}
