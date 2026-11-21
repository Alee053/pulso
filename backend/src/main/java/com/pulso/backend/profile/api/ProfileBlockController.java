package com.pulso.backend.profile.api;

import com.pulso.backend.profile.application.ManageProfileBlocks;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileBlockController {

    private final ManageProfileBlocks manageProfileBlocks;

    public ProfileBlockController(ManageProfileBlocks manageProfileBlocks) {
        this.manageProfileBlocks = manageProfileBlocks;
    }

    @PostMapping("/{targetId}/block")
    public ResponseEntity<Void> blockProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID targetId) {
        
        manageProfileBlocks.block(authenticatedProfileId(jwt), targetId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{targetId}/block")
    public ResponseEntity<Void> unblockProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID targetId) {
        
        manageProfileBlocks.unblock(authenticatedProfileId(jwt), targetId);
        return ResponseEntity.ok().build();
    }

    // Copiado de MeController para mantener la consistencia en el proyecto
    private static UUID authenticatedProfileId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }
}