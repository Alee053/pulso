package com.pulso.backend.report.domain;

import java.util.UUID;

public record ReportTarget(UUID profileId, UUID projectId, UUID messageId) {
    public ReportTarget {
        int count = (profileId == null ? 0 : 1)
                + (projectId == null ? 0 : 1)
                + (messageId == null ? 0 : 1);
        if (count != 1) {
            throw new ReportValidationException("Exactly one report target is required.");
        }
    }
}
