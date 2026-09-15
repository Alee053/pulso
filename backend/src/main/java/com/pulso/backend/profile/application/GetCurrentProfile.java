package com.pulso.backend.profile.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.profile.domain.Profile;

@Service
public class GetCurrentProfile {

    private final ProfileRepository repository;

    public GetCurrentProfile(ProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Profile execute(UUID authenticatedProfileId) {
        return repository.findById(authenticatedProfileId)
                .orElseThrow(ProfileNotFoundException::new);
    }
}
