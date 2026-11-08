package com.pulso.backend.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TextMessageContentTest {

    @Test
    void stripsSurroundingWhitespaceAndKeepsTheText() {
        assertThat(TextMessageContent.of("  Hola equipo  \n", 20).value()).isEqualTo("Hola equipo");
    }

    @Test
    void rejectsMissingOrBlankBodies() {
        assertThatThrownBy(() -> TextMessageContent.of(null, 20))
                .isInstanceOf(MessageValidationException.class)
                .hasMessage("body is required.");
        assertThatThrownBy(() -> TextMessageContent.of(" \t\n ", 20))
                .isInstanceOf(MessageValidationException.class)
                .hasMessage("body is required.");
    }

    @Test
    void acceptsABodyAtTheLimitAndRejectsOneCharacterMore() {
        assertThat(TextMessageContent.of("a".repeat(20), 20).value()).hasSize(20);

        assertThatThrownBy(() -> TextMessageContent.of("a".repeat(21), 20))
                .isInstanceOf(MessageValidationException.class)
                .hasMessage("body must not exceed 20 characters.");
    }

    @Test
    void countsCharactersInsteadOfUtf16Units() {
        String emojis = "😀".repeat(5);

        assertThat(TextMessageContent.of(emojis, 5).value()).isEqualTo(emojis);
    }

    @Test
    void deletedMessagesNeverExposeTheirBody() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-07T15:00:00Z");

        Message deleted = new Message(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MessageType.TEXT, MessageStatus.DELETED, "texto privado", now, null, now);
        Message sent = new Message(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MessageType.TEXT, MessageStatus.SENT, "texto visible", now, null, null);

        assertThat(deleted.body()).isNull();
        assertThat(sent.body()).isEqualTo("texto visible");
    }
}
