package com.pulso.backend.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfilePatch;
import com.pulso.backend.profile.domain.ProfileValidationException;

class UpdateCurrentProfileTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void updatesTheProfileIdentifiedByTheAuthenticatedSubject() {
        CapturingRepository repository = new CapturingRepository();
        ProfilePatch patch = new ProfilePatch(
                "organization",
                "Perfil actualizado",
                null,
                "Una descripción válida.",
                null,
                null,
                "private",
                null);

        Profile result = new UpdateCurrentProfile(repository).execute(PROFILE_ID, patch);

        assertThat(repository.profileId).isEqualTo(PROFILE_ID);
        assertThat(repository.patch).isSameAs(patch);
        assertThat(result.displayName()).isEqualTo("Perfil actualizado");
    }

    @Test
    void reportsWhenTheAuthenticatedProfileDoesNotExist() {
        ProfileRepository repository = new ProfileRepository() {
            @Override
            public Optional<Profile> findById(UUID profileId) {
                return Optional.empty();
            }

            @Override
            public Optional<Profile> update(UUID profileId, ProfilePatch patch) {
                return Optional.empty();
            }
        };

        assertThatThrownBy(() -> new UpdateCurrentProfile(repository).execute(
                PROFILE_ID,
                new ProfilePatch(null, "Perfil", null, null, null, null, null, null)))
                .isInstanceOf(ProfileNotFoundException.class)
                .hasMessage("Authenticated profile was not found.");
    }

    @Test
    void rejectsInvalidEditableValuesBeforePersistence() {
        assertThatThrownBy(() -> new ProfilePatch(
                "PERSON",
                "Perfil",
                null,
                null,
                null,
                null,
                "HIDDEN",
                null))
                .isInstanceOf(ProfileValidationException.class)
                .hasMessage("profileVisibility must be one of PUBLIC, MEMBERS_ONLY, PRIVATE.");
    }

    private static Profile profile() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-08T12:00:00Z");
        return new Profile(
                PROFILE_ID,
                "ORGANIZATION",
                "Perfil actualizado",
                null,
                "Una descripción válida.",
                null,
                null,
                "PRIVATE",
                "UNVERIFIED",
                "ACTIVE",
                "USER",
                null,
                timestamp,
                timestamp);
    }

    private static final class CapturingRepository implements ProfileRepository {

        private UUID profileId;
        private ProfilePatch patch;

        @Override
        public Optional<Profile> findById(UUID profileId) {
            return Optional.of(profile());
        }

        @Override
        public Optional<Profile> update(UUID profileId, ProfilePatch patch) {
            this.profileId = profileId;
            this.patch = patch;
            return Optional.of(profile());
        }
    }
}
