package com.pulso.backend.discovery.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.profile.api.ProfilePatchRequest;
import com.pulso.backend.profile.api.ProfileResponse;
import com.pulso.backend.profile.application.GetCurrentProfile;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.profile.application.UpdateCurrentProfile;
import com.pulso.backend.profile.domain.Profile;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final GetCurrentProfile getCurrentProfile;
    private final UpdateCurrentProfile updateCurrentProfile;

    public MeController(
            GetCurrentProfile getCurrentProfile,
            UpdateCurrentProfile updateCurrentProfile) {
        this.getCurrentProfile = getCurrentProfile;
        this.updateCurrentProfile = updateCurrentProfile;
    }

    @GetMapping
    public MeResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        Profile profile = getCurrentProfile.execute(authenticatedProfileId(jwt));
        return MeResponse.from(jwt, profile);
    }

    @PatchMapping
    public MeResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProfilePatchRequest request) {
        Profile profile = updateCurrentProfile.execute(
                authenticatedProfileId(jwt),
                request.toPatch());
        return MeResponse.from(jwt, profile);
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }

    public record MeResponse(
            String subject,
            String email,
            String role,
            List<String> audience,
            ProfileResponse profile) {

        private static MeResponse from(Jwt jwt, Profile profile) {
            return new MeResponse(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("role"),
                    jwt.getAudience(),
                    ProfileResponse.from(profile));
        }
    }
}
