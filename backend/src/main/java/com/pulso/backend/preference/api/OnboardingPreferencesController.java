package com.pulso.backend.preference.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.preference.application.GetOnboardingPreferences;
import com.pulso.backend.preference.application.SaveOnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/me/preferences")
public class OnboardingPreferencesController {

    private final GetOnboardingPreferences getOnboardingPreferences;
    private final SaveOnboardingPreferences saveOnboardingPreferences;

    public OnboardingPreferencesController(
            GetOnboardingPreferences getOnboardingPreferences,
            SaveOnboardingPreferences saveOnboardingPreferences) {
        this.getOnboardingPreferences = getOnboardingPreferences;
        this.saveOnboardingPreferences = saveOnboardingPreferences;
    }

    @GetMapping
    public OnboardingPreferencesResponse currentPreferences(
            @AuthenticationPrincipal Jwt jwt) {
        OnboardingPreferences preferences = getOnboardingPreferences.execute(
                authenticatedProfileId(jwt));
        return OnboardingPreferencesResponse.from(preferences);
    }

    @PutMapping
    public OnboardingPreferencesResponse replacePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) OnboardingPreferencesRequest request) {
        UUID profileId = authenticatedProfileId(jwt);
        OnboardingPreferences preferences = saveOnboardingPreferences.execute(
                profileId,
                request == null ? null : request.toPreferences(
                        profileId,
                        saveOnboardingPreferences.limits()));
        return OnboardingPreferencesResponse.from(preferences);
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidAuthenticatedSubjectException();
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }
}
