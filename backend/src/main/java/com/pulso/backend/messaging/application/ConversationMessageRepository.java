package com.pulso.backend.messaging.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.messaging.domain.ConversationAccess;
import com.pulso.backend.messaging.domain.Message;

public interface ConversationMessageRepository {

    /**
     * Locks the conversation for the current transaction when the profile is one
     * of its active participants. Empty means the conversation is missing or the
     * profile does not belong to it.
     */
    Optional<ConversationAccess> lockForActiveParticipant(UUID conversationId, UUID profileId);

    /**
     * Whether any block, in either direction, separates the profile from another
     * active participant. Must be called in the transaction that sends the message.
     */
    boolean isBlockedFromParticipants(UUID conversationId, UUID profileId);

    Message appendText(UUID conversationId, UUID senderProfileId, String body);

    /**
     * Reads one page ordered by creation time, authorized and read in a single
     * statement. Empty means the profile is not an active participant.
     */
    Optional<List<Message>> findPageForActiveParticipant(
            UUID conversationId,
            UUID profileId,
            int offset,
            int limit);
}
