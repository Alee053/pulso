package com.pulso.backend.connection.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.connection.application.CreateConnectionIntent;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/connection-intents")
public class ConnectionIntentController {

    private final CreateConnectionIntent createConnectionIntent;

    public ConnectionIntentController(CreateConnectionIntent createConnectionIntent) {
        this.createConnectionIntent = createConnectionIntent;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionIntentResponse create(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) CreateConnectionIntentRequest request) {
        if (request == null) {
            throw new InvalidRequestException("The request body is required.");
        }
        return ConnectionIntentResponse.from(createConnectionIntent.execute(
                projectId,
                authenticatedProfileId(jwt),
                request.opportunityId(),
                request.reason(),
                request.message()));
    }

    private static UUID authenticatedProfileId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidAuthenticatedSubjectException();
        }
        try {
            String subject = jwt.getSubject();
            UUID profileId = UUID.fromString(subject);
            if (!profileId.toString().equalsIgnoreCase(subject)) {
                throw new IllegalArgumentException("JWT subject is not a canonical profile UUID.");
            }
            return profileId;
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthenticatedSubjectException();
        }
    }
}
