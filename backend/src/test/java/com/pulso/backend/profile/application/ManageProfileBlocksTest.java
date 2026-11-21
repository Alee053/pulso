package com.pulso.backend.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfileBlock;
import com.pulso.backend.profile.domain.ProfilePatch;
import com.pulso.backend.profile.domain.ProfileValidationException;

class ManageProfileBlocksTest {
    private static final UUID ACTOR_ID = id("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = id("22222222-2222-2222-2222-222222222222");
    private final InMemoryRepository blocks = new InMemoryRepository();
    private final ManageProfileBlocks service = new ManageProfileBlocks(profiles("ACTIVE"), blocks);

    @Test
    void reportsBlockStatusAndSupportsIdempotentBlockAndUnblock() {
        assertThat(service.status(ACTOR_ID, TARGET_ID))
                .isEqualTo(ProfileBlock.unblocked(TARGET_ID));

        ProfileBlock first = service.block(ACTOR_ID, TARGET_ID);
        ProfileBlock repeated = service.block(ACTOR_ID, TARGET_ID);
        assertThat(first.blocked()).isTrue();
        assertThat(first.createdAt()).isNotNull();
        assertThat(repeated).isEqualTo(first);
        assertThat(service.status(ACTOR_ID, TARGET_ID)).isEqualTo(first);

        assertThat(service.unblock(ACTOR_ID, TARGET_ID))
                .isEqualTo(ProfileBlock.unblocked(TARGET_ID));
        assertThat(service.status(ACTOR_ID, TARGET_ID).blocked()).isFalse();
    }

    @Test
    void rejectsSelfBlocksAndInactiveOrUnknownTargetsBeforeChangingState() {
        assertThatThrownBy(() -> service.block(ACTOR_ID, ACTOR_ID))
                .isInstanceOf(ProfileValidationException.class)
                .hasMessage("A profile cannot block itself.");

        ManageProfileBlocks inactive = new ManageProfileBlocks(
                profiles("SUSPENDED"), blocks);
        assertThatThrownBy(() -> inactive.block(ACTOR_ID, TARGET_ID))
                .isInstanceOf(ProfileNotFoundException.class);

        ManageProfileBlocks missing = new ManageProfileBlocks(
                profiles(null), blocks);
        assertThatThrownBy(() -> missing.block(ACTOR_ID, TARGET_ID))
                .isInstanceOf(ProfileNotFoundException.class);
        assertThat(blocks.find(ACTOR_ID, TARGET_ID)).isEmpty();
    }

    private static Profile profile(String status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-21T12:00:00Z");
        return new Profile(TARGET_ID, "PERSON", "Ada", null, null, null, null,
                "PUBLIC", "UNVERIFIED", status, "USER", null, now, now);
    }

    private static UUID id(String value) { return UUID.fromString(value); }

    private static ProfileRepository profiles(String status) {
        return new ProfileRepository() {
            @Override
            public Optional<Profile> findById(UUID profileId) {
                return profileId.equals(TARGET_ID) && status != null
                        ? Optional.of(profile(status)) : Optional.empty();
            }

            @Override
            public Optional<Profile> update(UUID profileId, ProfilePatch patch) { return Optional.empty(); }
        };
    }

    private static final class InMemoryRepository implements ProfileBlockRepository {
        private final Map<String, ProfileBlock> state = new HashMap<>();

        @Override
        public Optional<ProfileBlock> find(UUID actor, UUID target) {
            return Optional.ofNullable(state.get(key(actor, target)));
        }

        @Override
        public ProfileBlock createIfAbsent(UUID actor, UUID target) {
            return state.computeIfAbsent(key(actor, target), ignored ->
                    ProfileBlock.blocked(target, OffsetDateTime.parse("2026-11-21T12:00:00Z")));
        }

        @Override
        public void delete(UUID actor, UUID target) { state.remove(key(actor, target)); }

        private static String key(UUID actor, UUID target) { return actor + ":" + target; }
    }
}
