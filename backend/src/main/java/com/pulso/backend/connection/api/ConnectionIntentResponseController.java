package com.pulso.backend.connection.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.connection.application.DeclineConnectionIntent;
import com.pulso.backend.connection.application.ListPendingConnectionIntents;
import com.pulso.backend.connection.application.WithdrawConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
public class ConnectionIntentResponseController {

    private final DeclineConnectionIntent declineConnectionIntent;
    private final WithdrawConnectionIntent withdrawConnectionIntent;
    private final ListPendingConnectionIntents listPendingConnectionIntents;

    public ConnectionIntentResponseController(
            DeclineConnectionIntent declineConnectionIntent,
            WithdrawConnectionIntent withdrawConnectionIntent,
            ListPendingConnectionIntents listPendingConnectionIntents) {
        this.declineConnectionIntent = declineConnectionIntent;
        this.withdrawConnectionIntent = withdrawConnectionIntent;
        this.listPendingConnectionIntents = listPendingConnectionIntents;
    }

    @GetMapping("/api/v1/connection-intents/pending")
    @ResponseStatus(HttpStatus.OK)
    public List<ConnectionIntentResponse> listPending(@AuthenticationPrincipal Jwt jwt) {
        List<ConnectionIntent> intents = listPendingConnectionIntents.execute(authenticatedProfileId(jwt));
        return intents.stream().map(ConnectionIntentResponse::from).toList();
    }

    @PostMapping("/api/v1/connection-intents/{connectionIntentId}/decline")
    @ResponseStatus(HttpStatus.OK)
    public ConnectionIntentResponse decline(
            @PathVariable UUID connectionIntentId,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectionIntent intent = declineConnectionIntent.execute(
                connectionIntentId,
                authenticatedProfileId(jwt));
        return ConnectionIntentResponse.from(intent);
    }

    @PostMapping("/api/v1/connection-intents/{connectionIntentId}/withdraw")
    @ResponseStatus(HttpStatus.OK)
    public ConnectionIntentResponse withdraw(
            @PathVariable UUID connectionIntentId,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectionIntent intent = withdrawConnectionIntent.execute(
                connectionIntentId,
                authenticatedProfileId(jwt));
        return ConnectionIntentResponse.from(intent);
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