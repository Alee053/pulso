package com.pulso.backend.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.config.MessageProperties;
import com.pulso.backend.messaging.domain.Message;
import com.pulso.backend.messaging.domain.MessageStatus;
import com.pulso.backend.messaging.domain.MessageType;
import com.pulso.backend.messaging.domain.MessageValidationException;

class SendTextMessageTest {

    private static final UUID CONVERSATION_ID = UUID.fromString("28444444-4444-4444-8444-444444444444");
    private static final UUID SENDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECIPIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OUTSIDER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private final InMemoryConversationMessageRepository repository =
            new InMemoryConversationMessageRepository(CONVERSATION_ID, SENDER_ID, RECIPIENT_ID);
    private final SendTextMessage sendTextMessage = new SendTextMessage(
            repository,
            new MessageProperties(0, 20, 50, 100000, 50));

    @Test
    void anActiveParticipantSendsATextMessageAsSent() {
        Message message = sendTextMessage.execute(CONVERSATION_ID, SENDER_ID, "  Hola, ¿conversamos?  ");

        assertThat(message.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(message.senderProfileId()).isEqualTo(SENDER_ID);
        assertThat(message.type()).isEqualTo(MessageType.TEXT);
        assertThat(message.status()).isEqualTo(MessageStatus.SENT);
        assertThat(message.body()).isEqualTo("Hola, ¿conversamos?");
        assertThat(repository.messages).containsExactly(message);
    }

    @Test
    void anExternalProfileCannotSendAndLearnsNothingAboutTheConversation() {
        assertThatThrownBy(() -> sendTextMessage.execute(CONVERSATION_ID, OUTSIDER_ID, "Hola"))
                .isInstanceOf(ConversationNotFoundException.class);

        assertThat(repository.appendCalls).isZero();
    }

    @Test
    void aParticipantWhoLeftCannotSend() {
        repository.activeParticipants.remove(RECIPIENT_ID);

        assertThatThrownBy(() -> sendTextMessage.execute(CONVERSATION_ID, RECIPIENT_ID, "Hola"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void anUnknownConversationLooksLikeAnInaccessibleOne() {
        assertThatThrownBy(() -> sendTextMessage.execute(UUID.randomUUID(), SENDER_ID, "Hola"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void conversationsThatAreNotActiveRejectNewMessages() {
        for (String status : new String[] {"ARCHIVED", "BLOCKED", "CLOSED"}) {
            repository.conversationStatus = status;

            assertThatThrownBy(() -> sendTextMessage.execute(CONVERSATION_ID, SENDER_ID, "Hola"))
                    .isInstanceOf(ConversationNotActiveException.class);
        }

        assertThat(repository.appendCalls).isZero();
    }

    @Test
    void aProfileBlockPreventsSending() {
        repository.blocked = true;

        assertThatThrownBy(() -> sendTextMessage.execute(CONVERSATION_ID, SENDER_ID, "Hola"))
                .isInstanceOf(MessagingBlockedException.class);

        assertThat(repository.appendCalls).isZero();
    }

    @Test
    void invalidBodiesAreRejectedBeforeTouchingPersistence() {
        assertThatThrownBy(() -> sendTextMessage.execute(CONVERSATION_ID, SENDER_ID, "   "))
                .isInstanceOf(MessageValidationException.class);
        assertThatThrownBy(() -> sendTextMessage.execute(CONVERSATION_ID, SENDER_ID, "x".repeat(51)))
                .isInstanceOf(MessageValidationException.class)
                .hasMessage("body must not exceed 50 characters.");

        assertThat(repository.appendCalls).isZero();
    }

    @Test
    void requiresAnAuthenticatedProfile() {
        assertThatThrownBy(() -> sendTextMessage.execute(CONVERSATION_ID, null, "Hola"))
                .isInstanceOf(MessageValidationException.class)
                .hasMessage("authenticatedProfileId is required.");
    }
}
