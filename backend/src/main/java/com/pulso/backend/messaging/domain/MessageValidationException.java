package com.pulso.backend.messaging.domain;

public class MessageValidationException extends RuntimeException {

    public MessageValidationException(String message) {
        super(message);
    }
}
