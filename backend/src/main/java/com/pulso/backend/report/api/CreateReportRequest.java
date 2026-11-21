package com.pulso.backend.report.api;

import java.util.UUID;

import com.pulso.backend.report.domain.ReportTarget;

public record CreateReportRequest(
        UUID targetProfileId, UUID targetProjectId, UUID targetMessageId, String reason, String description) {
    ReportTarget target() {
        return new ReportTarget(targetProfileId, targetProjectId, targetMessageId);
    }
}
