package com.pulso.backend.profile.application;

import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfilePatch;

public interface ProfileRepository {

    Optional<Profile> findById(UUID profileId);

    Optional<Profile> update(UUID profileId, ProfilePatch patch);
}
