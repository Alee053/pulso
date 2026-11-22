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
import com.pulso.backend.connection.application.ListConnectionIntents;
import com.pulso.backend.connection.application.WithdrawConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionInboxEntry;
import com.pulso.backend.connection.domain.ConnectionInboxView;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.profile.api.AuthenticatedProfileId;

@RestController
public class ConnectionIntentResponseController {

    private final DeclineConnectionIntent declineConnectionIntent;
    private final WithdrawConnectionIntent withdrawConnectionIntent;
    private final ListConnectionIntents listConnectionIntents;
    private final ConnectionInboxProperties properties;

    public ConnectionIntentResponseController(
            DeclineConnectionIntent declineConnectionIntent,
            WithdrawConnectionIntent withdrawConnectionIntent,
            ListConnectionIntents listConnectionIntents,
            ConnectionInboxProperties properties) {
        this.declineConnectionIntent = declineConnectionIntent;
        this.withdrawConnectionIntent = withdrawConnectionIntent;
        this.listConnectionIntents = listConnectionIntents;
        this.properties = properties;
    }

    @GetMapping("/api/v1/connection-intents/pending")
    @ResponseStatus(HttpStatus.OK)
    public List<ConnectionIntentResponse> listPending(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Integer limit) {
        return list(jwt, ConnectionInboxView.PENDING, offset, limit).stream()
                .map(entry -> ConnectionIntentResponse.from(entry.intent()))
                .toList();
    }

    @GetMapping("/api/v1/connection-intents/received")
    public List<ConnectionInboxResponse> listReceived(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Integer limit) {
        return list(jwt, ConnectionInboxView.RECEIVED, offset, limit).stream()
                .map(ConnectionInboxResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/connection-intents/sent")
    public List<ConnectionInboxResponse> listSent(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Integer limit) {
        return list(jwt, ConnectionInboxView.SENT, offset, limit).stream()
                .map(ConnectionInboxResponse::from)
                .toList();
    }

    private List<ConnectionInboxEntry> list(Jwt jwt, ConnectionInboxView view, int offset, Integer limit) {
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
        return listConnectionIntents.execute(profileId, view, offset, pageLimit);
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
