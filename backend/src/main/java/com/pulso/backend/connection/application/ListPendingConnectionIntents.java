package com.pulso.backend.connection.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.connection.domain.ConnectionIntent;

@Service
public class ListPendingConnectionIntents {

    private final ConnectionIntentAcceptanceRepository repository;

    public ListPendingConnectionIntents(ConnectionIntentAcceptanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ConnectionIntent> execute(UUID authenticatedProfileId) {
        return repository.findPendingByProjectMembership(authenticatedProfileId);
    }
}
