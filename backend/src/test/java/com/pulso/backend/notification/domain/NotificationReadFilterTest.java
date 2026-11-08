package com.pulso.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationReadFilterTest {

    @Test
    void defaultsToAllWhenNoStatusIsGiven() {
        assertThat(NotificationReadFilter.parse(null)).isEqualTo(NotificationReadFilter.ALL);
        assertThat(NotificationReadFilter.parse("  ")).isEqualTo(NotificationReadFilter.ALL);
    }

    @Test
    void parsesKnownStatusesIgnoringCaseAndWhitespace() {
        assertThat(NotificationReadFilter.parse("unread")).isEqualTo(NotificationReadFilter.UNREAD);
        assertThat(NotificationReadFilter.parse(" Read ")).isEqualTo(NotificationReadFilter.READ);
        assertThat(NotificationReadFilter.parse("ALL")).isEqualTo(NotificationReadFilter.ALL);
    }

    @Test
    void rejectsUnknownStatuses() {
        assertThatThrownBy(() -> NotificationReadFilter.parse("archived"))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessage("status must be ALL, UNREAD or READ.");
    }
}
