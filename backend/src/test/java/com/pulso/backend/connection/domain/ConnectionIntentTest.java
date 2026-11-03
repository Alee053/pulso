package com.pulso.backend.connection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConnectionIntentTest {

    private static final UUID ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID PROFILE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PROJECT_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID OPPORTUNITY_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-03T12:00:00Z");

    @Test
    void parsesEveryCanonicalReason() {
        assertThat(List.of(
                ConnectionIntentReason.parse("COLLABORATE"),
                ConnectionIntentReason.parse("WORK"),
                ConnectionIntentReason.parse("TRY_PRODUCT"),
                ConnectionIntentReason.parse("BUY"),
                ConnectionIntentReason.parse("INSTITUTION"),
                ConnectionIntentReason.parse("INVEST"),
                ConnectionIntentReason.parse("OFFER_SERVICE"),
                ConnectionIntentReason.parse("OTHER")))
                .containsExactly(
                        ConnectionIntentReason.COLLABORATE,
                        ConnectionIntentReason.WORK,
                        ConnectionIntentReason.TRY_PRODUCT,
                        ConnectionIntentReason.BUY,
                        ConnectionIntentReason.INSTITUTION,
                        ConnectionIntentReason.INVEST,
                        ConnectionIntentReason.OFFER_SERVICE,
                        ConnectionIntentReason.OTHER);
    }

    @Test
    void parsesReasonsWithoutDependingOnClientLetterCaseOrWhitespace() {
        assertThat(ConnectionIntentReason.parse("  offer_service "))
                .isEqualTo(ConnectionIntentReason.OFFER_SERVICE);
    }

    @Test
    void rejectsMissingBlankAndUnknownReasons() {
        assertThatThrownBy(() -> ConnectionIntentReason.parse(null))
                .isInstanceOf(ConnectionIntentValidationException.class);
        assertThatThrownBy(() -> ConnectionIntentReason.parse("  "))
                .isInstanceOf(ConnectionIntentValidationException.class);
        assertThatThrownBy(() -> ConnectionIntentReason.parse("HIRE"))
                .isInstanceOf(ConnectionIntentValidationException.class);
    }

    @Test
    void createsPendingProjectRequestWithoutAnOpportunityOrMessage() {
        ConnectionIntent intent = ConnectionIntent.pending(
                ID, PROFILE_ID, PROJECT_ID, null, ConnectionIntentReason.COLLABORATE, null, CREATED_AT);

        assertThat(intent.id()).isEqualTo(ID);
        assertThat(intent.fromProfileId()).isEqualTo(PROFILE_ID);
        assertThat(intent.projectId()).isEqualTo(PROJECT_ID);
        assertThat(intent.opportunityId()).isNull();
        assertThat(intent.reason()).isEqualTo(ConnectionIntentReason.COLLABORATE);
        assertThat(intent.message()).isNull();
        assertThat(intent.status()).isEqualTo("PENDING");
        assertThat(intent.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void createsOpportunityRequestAndNormalizesOptionalMessage() {
        ConnectionIntent intent = ConnectionIntent.pending(
                ID, PROFILE_ID, PROJECT_ID, OPPORTUNITY_ID, ConnectionIntentReason.WORK,
                "  I can help with the backend.  ", CREATED_AT);

        assertThat(intent.opportunityId()).isEqualTo(OPPORTUNITY_ID);
        assertThat(intent.message()).isEqualTo("I can help with the backend.");
        assertThat(intent.status()).isEqualTo("PENDING");
    }

    @Test
    void convertsABlankOptionalMessageToAbsent() {
        ConnectionIntent intent = ConnectionIntent.pending(
                ID, PROFILE_ID, PROJECT_ID, OPPORTUNITY_ID, ConnectionIntentReason.OTHER, "  ", CREATED_AT);

        assertThat(intent.message()).isNull();
    }

    @Test
    void rejectsMissingServerManagedIdentityAndCreationTime() {
        assertThatThrownBy(() -> ConnectionIntent.pending(
                null, PROFILE_ID, PROJECT_ID, null, ConnectionIntentReason.BUY, null, CREATED_AT))
                .isInstanceOf(ConnectionIntentValidationException.class);
        assertThatThrownBy(() -> ConnectionIntent.pending(
                ID, null, PROJECT_ID, null, ConnectionIntentReason.BUY, null, CREATED_AT))
                .isInstanceOf(ConnectionIntentValidationException.class);
        assertThatThrownBy(() -> ConnectionIntent.pending(
                ID, PROFILE_ID, null, null, ConnectionIntentReason.BUY, null, CREATED_AT))
                .isInstanceOf(ConnectionIntentValidationException.class);
        assertThatThrownBy(() -> ConnectionIntent.pending(
                ID, PROFILE_ID, PROJECT_ID, null, ConnectionIntentReason.BUY, null, null))
                .isInstanceOf(ConnectionIntentValidationException.class);
    }
}
