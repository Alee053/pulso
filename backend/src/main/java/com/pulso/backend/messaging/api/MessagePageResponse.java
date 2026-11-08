package com.pulso.backend.messaging.api;

import java.util.List;

import com.pulso.backend.messaging.application.MessagePage;

public record MessagePageResponse(List<MessageResponse> data, PageMetadata meta) {

    static MessagePageResponse from(MessagePage page, int offset, int limit) {
        return new MessagePageResponse(
                page.messages().stream().map(MessageResponse::from).toList(),
                new PageMetadata(offset, limit, page.hasMore()));
    }

    public record PageMetadata(int offset, int limit, boolean hasMore) {
    }
}
