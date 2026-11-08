package com.pulso.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationReadFilter;
import com.pulso.backend.notification.domain.NotificationType;
import com.pulso.backend.notification.domain.NotificationValidationException;

class ListNotificationsTest {

    private static final UUID RECIPIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
    private final ListNotifications listNotifications = new ListNotifications(repository);

    @Test
    void listsTheMostRecentNotificationsFirstWithPaginationMetadata() {
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_REQUEST, "2026-11-08T10:00:00Z", false);
        repository.add(RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_RESPONSE, "2026-11-08T11:00:00Z", true);

        NotificationPage firstPage = listNotifications.execute(
                RECIPIENT_ID, NotificationReadFilter.ALL, 0, 2);
        NotificationPage secondPage = listNotifications.execute(
                RECIPIENT_ID, NotificationReadFilter.ALL, 2, 2);

        assertThat(types(firstPage.notifications()))
                .containsExactly(NotificationType.NEW_MESSAGE, NotificationType.CONNECTION_RESPONSE);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(types(secondPage.notifications()))
                .containsExactly(NotificationType.CONNECTION_REQUEST);
        assertThat(secondPage.hasMore()).isFalse();
    }

    @Test
    void separatesReadFromUnreadNotifications() {
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_REQUEST, "2026-11-08T10:00:00Z", false);
        repository.add(RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", true);
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_RESPONSE, "2026-11-08T11:00:00Z", false);

        NotificationPage unread = listNotifications.execute(RECIPIENT_ID, NotificationReadFilter.UNREAD, 0, 20);
        NotificationPage read = listNotifications.execute(RECIPIENT_ID, NotificationReadFilter.READ, 0, 20);

        assertThat(types(unread.notifications()))
                .containsExactly(NotificationType.CONNECTION_RESPONSE, NotificationType.CONNECTION_REQUEST);
        assertThat(unread.notifications()).noneMatch(Notification::isRead);
        assertThat(types(read.notifications())).containsExactly(NotificationType.NEW_MESSAGE);
        assertThat(read.notifications()).allMatch(Notification::isRead);
        assertThat(unread.unreadCount()).isEqualTo(2);
        assertThat(read.unreadCount()).isEqualTo(2);
    }

    @Test
    void neverReturnsAnotherPersonsNotifications() {
        repository.add(OTHER_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_REQUEST, "2026-11-08T10:00:00Z", false);

        NotificationPage page = listNotifications.execute(RECIPIENT_ID, NotificationReadFilter.ALL, 0, 20);

        assertThat(page.notifications()).hasSize(1);
        assertThat(page.notifications()).allMatch(
                notification -> notification.recipientProfileId().equals(RECIPIENT_ID));
        assertThat(page.unreadCount()).isEqualTo(1);
    }

    @Test
    void anEmptyInboxReturnsAnEmptyPage() {
        NotificationPage page = listNotifications.execute(RECIPIENT_ID, NotificationReadFilter.ALL, 0, 20);

        assertThat(page.notifications()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.unreadCount()).isZero();
    }

    @Test
    void requiresTheAuthenticatedProfile() {
        assertThatThrownBy(() -> listNotifications.execute(null, NotificationReadFilter.ALL, 0, 20))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessage("authenticatedProfileId is required.");
    }

    private static List<NotificationType> types(List<Notification> notifications) {
        return notifications.stream().map(Notification::type).toList();
    }
}
