package com.pulso.backend.report.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportReceipt(UUID id, String status, Instant createdAt) {
}
