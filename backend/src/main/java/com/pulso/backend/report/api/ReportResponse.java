package com.pulso.backend.report.api;

import java.time.Instant;
import java.util.UUID;

import com.pulso.backend.report.domain.ReportReceipt;

public record ReportResponse(UUID id, String status, Instant createdAt) {
    static ReportResponse from(ReportReceipt receipt) {
        return new ReportResponse(receipt.id(), receipt.status(), receipt.createdAt());
    }
}
