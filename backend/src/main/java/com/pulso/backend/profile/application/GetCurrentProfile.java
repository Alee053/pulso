package com.pulso.backend.profile.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ProfileProperties;
import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfilePatch;

@Service
public class GetCurrentProfile {

    private final ProfileRepository repository;
    private final ProfileProperties properties;

    public GetCurrentProfile(
            ProfileRepository repository,
            ProfileProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public ProfilePatch.Limits limits() {
        return new ProfilePatch.Limits(
                properties.displayNameMaxLength(),
                properties.avatarPathMaxLength(),
                properties.shortBioMaxLength(),
                properties.cityMaxLength(),
                properties.departmentMaxLength());
    }

    @Transactional(readOnly = true)
    public Profile execute(UUID authenticatedProfileId) {
        return repository.findById(authenticatedProfileId)
                .orElseThrow(ProfileNotFoundException::new);
    }
}
