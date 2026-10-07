package com.pulso.backend.project.infrastructure;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.pulso.backend.ops.logging.ApplicationLogger;
import com.pulso.backend.ops.logging.LogContext;
import com.pulso.backend.project.application.MembershipAdministrationAccepted;

@Component
public class MembershipAdministrationAuditLogger {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void record(MembershipAdministrationAccepted event) {
        ApplicationLogger.info(
                new LogContext("project", "administerMembership", null, event.actorProfileId().toString()),
                "Project membership administration committed",
                Map.of(
                        "action", event.action(),
                        "projectId", event.projectId(),
                        "memberId", event.memberId(),
                        "previousRole", event.previousRole(),
                        "nextRole", event.nextRole(),
                        "previousStatus", event.previousStatus(),
                        "nextStatus", event.nextStatus()));
    }
}
