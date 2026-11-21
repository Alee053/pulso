package com.pulso.backend.connection.application;

public class ConnectionBlockedException extends RuntimeException {
    public ConnectionBlockedException() {
        super("No puedes enviar una solicitud a este usuario porque existe un bloqueo.");
    }
}