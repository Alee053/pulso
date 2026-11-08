package com.pulso.backend.messaging.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.messaging.domain.Message;
import com.pulso.backend.messaging.domain.MessageValidationException;

@Service
public class ListConversationMessages {

    private final ConversationMessageRepository repository;

    public ListConversationMessages(ConversationMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * Lists messages from oldest to newest. The caller validates the page window
     * against the configured limits.
     */
    public MessagePage execute(UUID conversationId, UUID authenticatedProfileId, int offset, int limit) {
        if (conversationId == null) {
            throw new MessageValidationException(ApiMessages.CONVERSATION_ID_REQUIRED);
        }
        if (authenticatedProfileId == null) {
            throw new MessageValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }

        List<Message> window = repository
                .findPageForActiveParticipant(conversationId, authenticatedProfileId, offset, limit + 1)
                .orElseThrow(ConversationNotFoundException::new);
        boolean hasMore = window.size() > limit;
        return new MessagePage(hasMore ? window.subList(0, limit) : window, hasMore);
    }
}
