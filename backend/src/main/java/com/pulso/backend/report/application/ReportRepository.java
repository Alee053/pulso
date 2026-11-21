package com.pulso.backend.report.application;

import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.report.domain.ReportReceipt;
import com.pulso.backend.report.domain.ReportTarget;

public interface ReportRepository {
    Optional<ReportReceipt> insertIfAccessible(UUID reporterId, ReportTarget target, String reason, String description);
}
