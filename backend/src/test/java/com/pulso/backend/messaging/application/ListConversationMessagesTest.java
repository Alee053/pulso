package com.pulso.backend.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.config.MessageProperties;
import com.pulso.backend.messaging.domain.Message;
import com.pulso.backend.messaging.domain.MessageValidationException;
import com.pulso.backend.notification.application.RecordingNotificationPublisher;

class ListConversationMessagesTest {

    private static final UUID CONVERSATION_ID = UUID.fromString("28444444-4444-4444-8444-444444444444");
    private static final UUID FIRST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OUTSIDER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private final InMemoryConversationMessageRepository repository =
            new InMemoryConversationMessageRepository(CONVERSATION_ID, FIRST_ID, SECOND_ID);
    private final ListConversationMessages listMessages = new ListConversationMessages(repository);
    private final SendTextMessage sendTextMessage = new SendTextMessage(
            repository,
            new MessageProperties(0, 20, 50, 100000, 100),
            new RecordingNotificationPublisher());

    @Test
    void returnsMessagesFromOldestToNewestWithPaginationMetadata() {
        sendTextMessage.execute(CONVERSATION_ID, FIRST_ID, "uno");
        sendTextMessage.execute(CONVERSATION_ID, SECOND_ID, "dos");
        sendTextMessage.execute(CONVERSATION_ID, FIRST_ID, "tres");

        MessagePage firstPage = listMessages.execute(CONVERSATION_ID, SECOND_ID, 0, 2);
        MessagePage secondPage = listMessages.execute(CONVERSATION_ID, SECOND_ID, 2, 2);

        assertThat(bodies(firstPage.messages())).containsExactly("uno", "dos");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(bodies(secondPage.messages())).containsExactly("tres");
        assertThat(secondPage.hasMore()).isFalse();
    }

    @Test
    void anEmptyConversationReturnsAnEmptyPageForAParticipant() {
        MessagePage page = listMessages.execute(CONVERSATION_ID, FIRST_ID, 0, 20);

        assertThat(page.messages()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void anExternalProfileCannotReadTheConversation() {
        sendTextMessage.execute(CONVERSATION_ID, FIRST_ID, "privado");

        assertThatThrownBy(() -> listMessages.execute(CONVERSATION_ID, OUTSIDER_ID, 0, 20))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void aParticipantWhoLeftCannotReadTheConversation() {
        repository.activeParticipants.remove(SECOND_ID);

        assertThatThrownBy(() -> listMessages.execute(CONVERSATION_ID, SECOND_ID, 0, 20))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void requiresTheConversationAndTheAuthenticatedProfile() {
        assertThatThrownBy(() -> listMessages.execute(null, FIRST_ID, 0, 20))
                .isInstanceOf(MessageValidationException.class);
        assertThatThrownBy(() -> listMessages.execute(CONVERSATION_ID, null, 0, 20))
                .isInstanceOf(MessageValidationException.class);
    }

    private static List<String> bodies(List<Message> messages) {
        return messages.stream().map(Message::body).toList();
    }
}
