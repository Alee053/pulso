package com.pulso.backend.messaging.application;

import java.util.List;

import com.pulso.backend.messaging.domain.Message;

public record MessagePage(List<Message> messages, boolean hasMore) {
}
