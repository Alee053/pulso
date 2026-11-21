package com.pulso.backend.connection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;
import com.pulso.backend.connection.domain.ConnectionIntentValidationException;
import com.pulso.backend.notification.application.RecordingNotificationPublisher;

class CreateConnectionIntentTest {

    private static final UUID SENDER_ID = id("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT_ID = id("22222222-2222-2222-2222-222222222222");
    private static final UUID OPPORTUNITY_ID = id("33333333-3333-3333-3333-333333333333");
    private static final UUID INTENT_ID = id("44444444-4444-4444-4444-444444444444");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-03T12:00:00Z");

    @Test
    void createsAProjectOnlyRequestForTheAuthenticatedSender() {
        RecordingRepository repository = new RecordingRepository();
        RecordingNotificationPublisher notifications = new RecordingNotificationPublisher();

        ConnectionIntent created = new CreateConnectionIntent(repository, notifications).execute(
                PROJECT_ID, SENDER_ID, null, "COLLABORATE", null);

        assertThat(notifications.requests).containsExactly(
                new RecordingNotificationPublisher.ConnectionRequested(INTENT_ID, PROJECT_ID, SENDER_ID));
        assertThat(repository.callCount).isEqualTo(1);
        assertThat(repository.senderId).isEqualTo(SENDER_ID);
        assertThat(repository.projectId).isEqualTo(PROJECT_ID);
        assertThat(repository.opportunityId).isNull();
        assertThat(repository.reason).isEqualTo(ConnectionIntentReason.COLLABORATE);
        assertThat(repository.message).isNull();
        assertThat(created.status()).isEqualTo("PENDING");
    }

    @Test
    void createsAnOpportunityRequestWithAnOptionalMessage() {
        RecordingRepository repository = new RecordingRepository();

        ConnectionIntent created = new CreateConnectionIntent(
                repository, new RecordingNotificationPublisher()).execute(
                PROJECT_ID, SENDER_ID, OPPORTUNITY_ID, " offer_service ", "Me interesa colaborar.");

        assertThat(repository.senderId).isEqualTo(SENDER_ID);
        assertThat(repository.projectId).isEqualTo(PROJECT_ID);
        assertThat(repository.opportunityId).isEqualTo(OPPORTUNITY_ID);
        assertThat(repository.reason).isEqualTo(ConnectionIntentReason.OFFER_SERVICE);
        assertThat(repository.message).isEqualTo("Me interesa colaborar.");
        assertThat(created.opportunityId()).isEqualTo(OPPORTUNITY_ID);
        assertThat(created.status()).isEqualTo("PENDING");
    }

    @Test
    void propagatesInactiveSenderUnavailableContextAndDuplicateRejections() {
        for (ConnectionIntentRejectedException.Reason reason : ConnectionIntentRejectedException.Reason.values()) {
            RecordingRepository repository = new RecordingRepository();
            repository.failure = new ConnectionIntentRejectedException(reason);
            RecordingNotificationPublisher notifications = new RecordingNotificationPublisher();

            assertThatThrownBy(() -> new CreateConnectionIntent(repository, notifications).execute(
                    PROJECT_ID, SENDER_ID, null, "OTHER", null))
                    .isSameAs(repository.failure);
            assertThat(repository.callCount).isEqualTo(1);
            assertThat(notifications.isEmpty()).isTrue();
        }
    }

    @Test
    void invalidReasonDoesNotCallTheRepository() {
        RecordingRepository repository = new RecordingRepository();

        RecordingNotificationPublisher notifications = new RecordingNotificationPublisher();

        assertThatThrownBy(() -> new CreateConnectionIntent(repository, notifications).execute(
                PROJECT_ID, SENDER_ID, null, "UNKNOWN", null))
                .isInstanceOf(ConnectionIntentValidationException.class);

        assertThat(repository.callCount).isZero();
        assertThat(notifications.isEmpty()).isTrue();
    }

    @Test
    void missingAuthenticatedSenderOrProjectDoesNotCallTheRepository() {
        RecordingRepository repository = new RecordingRepository();
        CreateConnectionIntent useCase = new CreateConnectionIntent(
                repository, new RecordingNotificationPublisher());

        assertThatThrownBy(() -> useCase.execute(PROJECT_ID, null, null, "OTHER", null))
                .isInstanceOf(ConnectionIntentValidationException.class);
        assertThatThrownBy(() -> useCase.execute(null, SENDER_ID, null, "OTHER", null))
                .isInstanceOf(ConnectionIntentValidationException.class);

        assertThat(repository.callCount).isZero();
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }

    private static final class RecordingRepository implements ConnectionIntentRepository {

        private int callCount;
        private UUID senderId;
        private UUID projectId;
        private UUID opportunityId;
        private ConnectionIntentReason reason;
        private String message;
        private RuntimeException failure;

        @Override
        public ConnectionIntent createPending(
                UUID senderProfileId,
                UUID targetProjectId,
                UUID targetOpportunityId,
                ConnectionIntentReason targetReason,
                String targetMessage) {
            callCount++;
            senderId = senderProfileId;
            projectId = targetProjectId;
            opportunityId = targetOpportunityId;
            reason = targetReason;
            message = targetMessage;
            if (failure != null) {
                throw failure;
            }
            return ConnectionIntent.pending(
                    INTENT_ID,
                    senderProfileId,
                    targetProjectId,
                    targetOpportunityId,
                    targetReason,
                    targetMessage,
                    CREATED_AT);
        }
    }
}
