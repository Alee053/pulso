package com.pulso.backend.profile.application;

import com.pulso.backend.profile.domain.ProfileBlockValidationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ManageProfileBlocks {

    private final ProfileBlockRepository repository;

    public ManageProfileBlocks(ProfileBlockRepository repository) {
        this.repository = repository;
    }

    public void block(UUID currentUserId, UUID targetId) {
        if (currentUserId.equals(targetId)) {
            throw new ProfileBlockValidationException("No puedes bloquearte a ti mismo.");
        }
        repository.block(currentUserId, targetId);
    }

    public void unblock(UUID currentUserId, UUID targetId) {
        repository.unblock(currentUserId, targetId);
    }
}