package com.pulso.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationType;
import com.pulso.backend.notification.domain.NotificationValidationException;

class MarkNotificationReadTest {

    private static final UUID RECIPIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
    private final MarkNotificationRead markNotificationRead = new MarkNotificationRead(repository);

    @Test
    void theRecipientMarksTheirNotificationAsRead() {
        Notification unread = repository.add(
                RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);

        Notification read = markNotificationRead.execute(unread.id(), RECIPIENT_ID);

        assertThat(read.isRead()).isTrue();
        assertThat(read.readAt()).isEqualTo(repository.readClock);
        assertThat(repository.countUnread(RECIPIENT_ID)).isZero();
    }

    @Test
    void markingAgainIsIdempotentAndKeepsTheFirstReadTime() {
        Notification unread = repository.add(
                RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);
        Notification first = markNotificationRead.execute(unread.id(), RECIPIENT_ID);
        repository.readClock = OffsetDateTime.parse("2026-11-08T19:00:00Z");

        Notification second = markNotificationRead.execute(unread.id(), RECIPIENT_ID);

        assertThat(second.readAt()).isEqualTo(first.readAt());
    }

    @Test
    void anotherPersonCannotMarkTheNotificationAndLearnsNothingAboutIt() {
        Notification unread = repository.add(
                RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);

        assertThatThrownBy(() -> markNotificationRead.execute(unread.id(), OTHER_ID))
                .isInstanceOf(NotificationNotFoundException.class);
        assertThatThrownBy(() -> markNotificationRead.execute(UUID.randomUUID(), OTHER_ID))
                .isInstanceOf(NotificationNotFoundException.class);

        assertThat(repository.countUnread(RECIPIENT_ID)).isEqualTo(1);
    }

    @Test
    void requiresTheNotificationAndTheAuthenticatedProfile() {
        assertThatThrownBy(() -> markNotificationRead.execute(null, RECIPIENT_ID))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> markNotificationRead.execute(UUID.randomUUID(), null))
                .isInstanceOf(NotificationValidationException.class);
    }
}
