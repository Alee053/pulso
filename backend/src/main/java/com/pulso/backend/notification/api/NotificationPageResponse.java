package com.pulso.backend.notification.api;

import java.util.List;

import com.pulso.backend.notification.application.NotificationPage;

public record NotificationPageResponse(List<NotificationResponse> data, PageMetadata meta) {

    static NotificationPageResponse from(NotificationPage page, int offset, int limit) {
        return new NotificationPageResponse(
                page.notifications().stream().map(NotificationResponse::from).toList(),
                new PageMetadata(offset, limit, page.hasMore(), page.unreadCount()));
    }

    public record PageMetadata(int offset, int limit, boolean hasMore, long unreadCount) {
    }
}
