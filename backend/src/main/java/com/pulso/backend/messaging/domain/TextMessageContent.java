package com.pulso.backend.messaging.domain;

import com.pulso.backend.config.ApiMessages;

/**
 * Validated body of a new {@code TEXT} message. Surrounding whitespace is not
 * part of the message; the length limit applies to the stored text.
 */
public record TextMessageContent(String value) {

    public static TextMessageContent of(String rawBody, int maxLength) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new MessageValidationException(ApiMessages.MESSAGE_BODY_REQUIRED);
        }

        String body = rawBody.strip();
        if (body.codePointCount(0, body.length()) > maxLength) {
            throw new MessageValidationException(
                    String.format(ApiMessages.MESSAGE_BODY_MAX_LENGTH, maxLength));
        }
        return new TextMessageContent(body);
    }
}
