package com.pulso.backend.profile.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.profile.application.ManageProfileBlocks;
import com.pulso.backend.profile.domain.ProfileBlock;

@RestController
@RequestMapping("/api/v1/me/blocks")
public class ProfileBlockController {
    private final ManageProfileBlocks manageProfileBlocks;

    public ProfileBlockController(ManageProfileBlocks manageProfileBlocks) {
        this.manageProfileBlocks = manageProfileBlocks;
    }

    @GetMapping("/{profileId}")
    public ProfileBlockResponse status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId) {
        return ProfileBlockResponse.from(manageProfileBlocks.status(authenticatedProfileId(jwt), profileId));
    }

    @PutMapping("/{profileId}")
    public ProfileBlockResponse block(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId) {
        return ProfileBlockResponse.from(manageProfileBlocks.block(authenticatedProfileId(jwt), profileId));
    }

    @DeleteMapping("/{profileId}")
    public ProfileBlockResponse unblock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID profileId) {
        return ProfileBlockResponse.from(manageProfileBlocks.unblock(authenticatedProfileId(jwt), profileId));
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new InvalidAuthenticatedSubjectException();
        try {
            UUID profileId = UUID.fromString(jwt.getSubject());
            if (!profileId.toString().equalsIgnoreCase(jwt.getSubject())) {
                throw new IllegalArgumentException("JWT subject is not a canonical profile UUID.");
            }
            return profileId;
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }

    public record ProfileBlockResponse(UUID targetProfileId, boolean blocked, OffsetDateTime createdAt) {
        private static ProfileBlockResponse from(ProfileBlock block) {
            return new ProfileBlockResponse(block.targetProfileId(), block.blocked(), block.createdAt());
        }
    }
}
