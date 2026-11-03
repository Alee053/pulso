package com.pulso.backend.connection.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.connection.application.AcceptConnectionIntent;
import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
public class ConnectionIntentAcceptanceController {

    private final AcceptConnectionIntent acceptConnectionIntent;

    public ConnectionIntentAcceptanceController(AcceptConnectionIntent acceptConnectionIntent) {
        this.acceptConnectionIntent = acceptConnectionIntent;
    }

    @PostMapping("/api/v1/connection-intents/{connectionIntentId}/accept")
    @ResponseStatus(HttpStatus.OK)
    public AcceptedConnectionConversationResponse accept(
            @PathVariable UUID connectionIntentId,
            @AuthenticationPrincipal Jwt jwt) {
        AcceptedConnectionConversation accepted = acceptConnectionIntent.execute(
                connectionIntentId,
                authenticatedProfileId(jwt));
        return AcceptedConnectionConversationResponse.from(accepted);
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
