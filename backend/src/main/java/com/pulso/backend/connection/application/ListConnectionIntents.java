package com.pulso.backend.connection.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.connection.domain.ConnectionInboxEntry;
import com.pulso.backend.connection.domain.ConnectionInboxView;

@Service
public class ListConnectionIntents {

    private final ConnectionIntentAcceptanceRepository repository;

    public ListConnectionIntents(ConnectionIntentAcceptanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ConnectionInboxEntry> execute(
            UUID authenticatedProfileId, ConnectionInboxView view, int offset, int limit) {
        return repository.findInbox(authenticatedProfileId, view, offset, limit);
    }
}
