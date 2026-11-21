package com.pulso.backend.connection.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ConnectionInboxProperties;
import com.pulso.backend.connection.application.DeclineConnectionIntent;
import com.pulso.backend.connection.application.ListPendingConnectionIntents;
import com.pulso.backend.connection.application.WithdrawConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.profile.api.AuthenticatedProfileId;

@RestController
public class ConnectionIntentResponseController {

    private final DeclineConnectionIntent declineConnectionIntent;
    private final WithdrawConnectionIntent withdrawConnectionIntent;
    private final ListPendingConnectionIntents listPendingConnectionIntents;
    private final ConnectionInboxProperties properties;

    public ConnectionIntentResponseController(
            DeclineConnectionIntent declineConnectionIntent,
            WithdrawConnectionIntent withdrawConnectionIntent,
            ListPendingConnectionIntents listPendingConnectionIntents,
            ConnectionInboxProperties properties) {
        this.declineConnectionIntent = declineConnectionIntent;
        this.withdrawConnectionIntent = withdrawConnectionIntent;
        this.listPendingConnectionIntents = listPendingConnectionIntents;
        this.properties = properties;
    }

    @GetMapping("/api/v1/connection-intents/pending")
    @ResponseStatus(HttpStatus.OK)
    public List<ConnectionIntentResponse> listPending(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Integer limit) {
        UUID profileId = AuthenticatedProfileId.require(jwt);
        int pageLimit = limit == null ? properties.defaultLimit() : limit;
        if (offset < 0 || offset > properties.maxOffset()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
        if (pageLimit < 1 || pageLimit > properties.maxLimit()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }
        List<ConnectionIntent> intents = listPendingConnectionIntents.execute(profileId, offset, pageLimit);
        return intents.stream().map(ConnectionIntentResponse::from).toList();
    }

    @PostMapping("/api/v1/connection-intents/{connectionIntentId}/decline")
    @ResponseStatus(HttpStatus.OK)
    public ConnectionIntentResponse decline(
            @PathVariable UUID connectionIntentId,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectionIntent intent = declineConnectionIntent.execute(
                connectionIntentId,
                AuthenticatedProfileId.require(jwt));
        return ConnectionIntentResponse.from(intent);
    }

    @PostMapping("/api/v1/connection-intents/{connectionIntentId}/withdraw")
    @ResponseStatus(HttpStatus.OK)
    public ConnectionIntentResponse withdraw(
            @PathVariable UUID connectionIntentId,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectionIntent intent = withdrawConnectionIntent.execute(
                connectionIntentId,
                AuthenticatedProfileId.require(jwt));
        return ConnectionIntentResponse.from(intent);
    }
}
