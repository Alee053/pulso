package com.pulso.backend.connection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.connection.domain.ConnectionIntent;

class AcceptConnectionIntentTest {

    private static final UUID INTENT_ID = UUID.fromString("27333333-3333-4333-8333-333333333333");
    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID INITIATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESPONDER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_MEMBER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID CONVERSATION_ID = UUID.fromString("27444444-4444-4444-8444-444444444444");

    @Test
    void acceptsAPendingIntentAndCreatesTheProjectContactConversation() {
        FakeRepository repository = pendingRepository();

        AcceptedConnectionConversation accepted = service(repository).execute(INTENT_ID, RESPONDER_ID);

        assertThat(accepted.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(accepted.conversationType()).isEqualTo("PROJECT_CONTACT");
        assertThat(accepted.participants())
                .extracting(AcceptedConnectionConversation.Participant::role)
                .containsExactly("INITIATOR", "PROJECT_MEMBER");
        assertThat(repository.createCalls).isEqualTo(1);
    }

    @Test
    void returnsTheExistingConversationForARetryByTheSameResponder() {
        FakeRepository repository = pendingRepository();
        AcceptedConnectionConversation first = service(repository).execute(INTENT_ID, RESPONDER_ID);
        repository.activeMember = false;

        AcceptedConnectionConversation retry = service(repository).execute(INTENT_ID, RESPONDER_ID);

        assertThat(retry).isEqualTo(first);
        assertThat(repository.createCalls).isEqualTo(1);
    }

    @Test
    void returnsTheExistingConversationAfterTheIntentWasConverted() {
        FakeRepository repository = pendingRepository();
        AcceptedConnectionConversation first = service(repository).execute(INTENT_ID, RESPONDER_ID);
        repository.intent = intent(ConnectionIntent.Status.CONVERTED, RESPONDER_ID);

        AcceptedConnectionConversation retry = service(repository).execute(INTENT_ID, RESPONDER_ID);

        assertThat(retry).isEqualTo(first);
        assertThat(repository.createCalls).isEqualTo(1);
    }

    @Test
    void rejectsARetryByAnotherProjectMember() {
        FakeRepository repository = pendingRepository();
        service(repository).execute(INTENT_ID, RESPONDER_ID);

        assertThatThrownBy(() -> service(repository).execute(INTENT_ID, OTHER_MEMBER_ID))
                .isInstanceOf(ConnectionIntentConflictException.class);
        assertThat(repository.createCalls).isEqualTo(1);
    }

    @Test
    void rejectsAnActorWithoutActiveProjectMembership() {
        FakeRepository repository = pendingRepository();
        repository.activeMember = false;

        assertThatThrownBy(() -> service(repository).execute(INTENT_ID, RESPONDER_ID))
                .isInstanceOf(ConnectionIntentPermissionException.class)
                .hasMessageContaining("Active project membership");
        assertThat(repository.createCalls).isZero();
    }

    @Test
    void rejectsBlockedProfilesBeforeCreatingAConversation() {
        FakeRepository repository = pendingRepository();
        repository.blocked = true;

        assertThatThrownBy(() -> service(repository).execute(INTENT_ID, RESPONDER_ID))
                .isInstanceOf(ConnectionIntentPermissionException.class)
                .hasMessageContaining("Blocked profiles");
        assertThat(repository.createCalls).isZero();
    }

    @Test
    void rejectsSelfAcceptanceAndResolvedIntents() {
        FakeRepository selfRepository = new FakeRepository(intent(
                ConnectionIntent.Status.PENDING,
                null));
        assertThatThrownBy(() -> service(selfRepository).execute(INTENT_ID, INITIATOR_ID))
                .isInstanceOf(ConnectionIntentConflictException.class);

        FakeRepository declinedRepository = new FakeRepository(intent(
                ConnectionIntent.Status.DECLINED,
                RESPONDER_ID));
        assertThatThrownBy(() -> service(declinedRepository).execute(INTENT_ID, RESPONDER_ID))
                .isInstanceOf(ConnectionIntentConflictException.class);
    }

    @Test
    void rejectsAnUnknownIntent() {
        FakeRepository repository = new FakeRepository(null);

        assertThatThrownBy(() -> service(repository).execute(INTENT_ID, RESPONDER_ID))
                .isInstanceOf(ConnectionIntentNotFoundException.class);
    }

    private static AcceptConnectionIntent service(FakeRepository repository) {
        return new AcceptConnectionIntent(repository);
    }

    private static FakeRepository pendingRepository() {
        return new FakeRepository(intent(ConnectionIntent.Status.PENDING, null));
    }

    private static ConnectionIntent intent(ConnectionIntent.Status status, UUID responderId) {
        return new ConnectionIntent(INTENT_ID, INITIATOR_ID, PROJECT_ID, status, responderId);
    }

    private static final class FakeRepository implements ConnectionIntentAcceptanceRepository {

        private ConnectionIntent intent;
        private boolean activeMember = true;
        private boolean blocked;
        private int createCalls;
        private AcceptedConnectionConversation conversation;

        private FakeRepository(ConnectionIntent intent) {
            this.intent = intent;
        }

        @Override
        public Optional<ConnectionIntent> findByIdForUpdate(UUID connectionIntentId) {
            return Optional.ofNullable(intent);
        }

        @Override
        public boolean hasActiveProjectMembership(UUID projectId, UUID profileId) {
            return activeMember;
        }

        @Override
        public boolean profilesAreBlocked(UUID firstProfileId, UUID secondProfileId) {
            return blocked;
        }

        @Override
        public Optional<AcceptedConnectionConversation> findConversation(UUID connectionIntentId) {
            return Optional.ofNullable(conversation);
        }

        @Override
        public AcceptedConnectionConversation acceptAndCreateConversation(
                ConnectionIntent connectionIntent,
                UUID responderProfileId) {
            createCalls++;
            intent = new ConnectionIntent(
                    connectionIntent.id(),
                    connectionIntent.fromProfileId(),
                    connectionIntent.projectId(),
                    ConnectionIntent.Status.ACCEPTED,
                    responderProfileId);
            conversation = new AcceptedConnectionConversation(
                    INTENT_ID,
                    CONVERSATION_ID,
                    PROJECT_ID,
                    INITIATOR_ID,
                    responderProfileId);
            return conversation;
        }
    }
}
