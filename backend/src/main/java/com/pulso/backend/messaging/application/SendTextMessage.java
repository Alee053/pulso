package com.pulso.backend.messaging.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.MessageProperties;
import com.pulso.backend.messaging.domain.ConversationAccess;
import com.pulso.backend.messaging.domain.Message;
import com.pulso.backend.messaging.domain.MessageValidationException;
import com.pulso.backend.messaging.domain.TextMessageContent;
import com.pulso.backend.notification.application.NotificationPublisher;

@Service
public class SendTextMessage {

    private final ConversationMessageRepository repository;
    private final MessageProperties properties;
    private final NotificationPublisher notifications;

    public SendTextMessage(
            ConversationMessageRepository repository,
            MessageProperties properties,
            NotificationPublisher notifications) {
        this.repository = repository;
        this.properties = properties;
        this.notifications = notifications;
    }

    @Transactional
    public Message execute(UUID conversationId, UUID authenticatedProfileId, String body) {
        if (conversationId == null) {
            throw new MessageValidationException(ApiMessages.CONVERSATION_ID_REQUIRED);
        }
        if (authenticatedProfileId == null) {
            throw new MessageValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
        TextMessageContent content = TextMessageContent.of(body, properties.bodyMaxLength());

        ConversationAccess access = repository
                .lockForActiveParticipant(conversationId, authenticatedProfileId)
                .orElseThrow(ConversationNotFoundException::new);
        if (!access.acceptsMessages()) {
            throw new ConversationNotActiveException();
        }
        if (repository.isBlockedFromParticipants(conversationId, authenticatedProfileId)) {
            throw new MessagingBlockedException();
        }

        Message message = repository.appendText(conversationId, authenticatedProfileId, content.value());
        notifications.messageSent(conversationId, message.id(), authenticatedProfileId);
        return message;
    }
}
