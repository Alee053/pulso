package com.pulso.backend.profile.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfilePatch;

@Service
public class UpdateCurrentProfile {

    private final ProfileRepository repository;

    public UpdateCurrentProfile(ProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Profile execute(UUID authenticatedProfileId, ProfilePatch patch) {
        return repository.update(authenticatedProfileId, patch)
                .orElseThrow(ProfileNotFoundException::new);
    }
}
