package com.pulso.backend.connection.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pulso.backend.connection.domain.ConnectionIntent;

@Service
public class WithdrawConnectionIntent {
    private final ConnectionIntentAcceptanceRepository repository;

    public WithdrawConnectionIntent(ConnectionIntentAcceptanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ConnectionIntent execute(UUID connectionIntentId, UUID authenticatedProfileId) {
        ConnectionIntent intent = repository.findByIdForUpdate(connectionIntentId)
                .orElseThrow(ConnectionIntentNotFoundException::new);

        if (!intent.fromProfileId().equals(authenticatedProfileId)) {
            throw ConnectionIntentPermissionException.membershipRequired(); // O un error de "Solo el remitente puede cancelar"
        }
        if (!intent.isPending()) {
            throw new ConnectionIntentConflictException();
        }

        ConnectionIntent withdrawn = intent.withdraw();
        repository.updateStatus(withdrawn, null);
        return withdrawn;
    }
}