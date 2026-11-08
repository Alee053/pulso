package com.pulso.backend.messaging.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.pulso.backend.messaging.domain.ConversationAccess;
import com.pulso.backend.messaging.domain.Message;
import com.pulso.backend.messaging.domain.MessageStatus;
import com.pulso.backend.messaging.domain.MessageType;

/**
 * Single-conversation fake that mirrors the repository contract: only active
 * participants are authorized and pages are ordered from oldest to newest.
 */
public final class InMemoryConversationMessageRepository implements ConversationMessageRepository {

    public final UUID conversationId;
    public String conversationStatus = "ACTIVE";
    public boolean blocked;
    public final Set<UUID> activeParticipants = new HashSet<>();
    public final List<Message> messages = new ArrayList<>();
    public int appendCalls;

    private OffsetDateTime clock = OffsetDateTime.parse("2026-11-07T15:00:00Z");

    public InMemoryConversationMessageRepository(UUID conversationId, UUID... participants) {
        this.conversationId = conversationId;
        activeParticipants.addAll(List.of(participants));
    }

    @Override
    public Optional<ConversationAccess> lockForActiveParticipant(UUID requestedConversationId, UUID profileId) {
        if (!conversationId.equals(requestedConversationId) || !activeParticipants.contains(profileId)) {
            return Optional.empty();
        }
        return Optional.of(new ConversationAccess(conversationId, conversationStatus));
    }

    @Override
    public boolean isBlockedFromParticipants(UUID requestedConversationId, UUID profileId) {
        return blocked;
    }

    @Override
    public Message appendText(UUID requestedConversationId, UUID senderProfileId, String body) {
        appendCalls++;
        clock = clock.plusSeconds(1);
        Message message = new Message(
                UUID.randomUUID(),
                requestedConversationId,
                senderProfileId,
                MessageType.TEXT,
                MessageStatus.SENT,
                body,
                clock,
                null,
                null);
        messages.add(message);
        return message;
    }

    @Override
    public Optional<List<Message>> findPageForActiveParticipant(
            UUID requestedConversationId,
            UUID profileId,
            int offset,
            int limit) {
        if (!conversationId.equals(requestedConversationId) || !activeParticipants.contains(profileId)) {
            return Optional.empty();
        }
        return Optional.of(messages.stream()
                .sorted(java.util.Comparator.comparing(Message::createdAt).thenComparing(Message::id))
                .skip(offset)
                .limit(limit)
                .toList());
    }
}
