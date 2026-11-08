package com.pulso.backend.notification.application;

import java.util.List;

import com.pulso.backend.notification.domain.Notification;

public record NotificationPage(List<Notification> notifications, boolean hasMore, long unreadCount) {
}
