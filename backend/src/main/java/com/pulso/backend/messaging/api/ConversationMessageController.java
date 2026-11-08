package com.pulso.backend.messaging.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.MessageProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.messaging.application.ListConversationMessages;
import com.pulso.backend.messaging.application.MessagePage;
import com.pulso.backend.messaging.application.SendTextMessage;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
public class ConversationMessageController {

    private final SendTextMessage sendTextMessage;
    private final ListConversationMessages listConversationMessages;
    private final MessageProperties properties;

    public ConversationMessageController(
            SendTextMessage sendTextMessage,
            ListConversationMessages listConversationMessages,
            MessageProperties properties) {
        this.sendTextMessage = sendTextMessage;
        this.listConversationMessages = listConversationMessages;
        this.properties = properties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) SendMessageRequest request) {
        if (request == null) {
            throw new InvalidRequestException(ApiMessages.REQUEST_BODY_REQUIRED);
        }
        return MessageResponse.from(sendTextMessage.execute(
                conversationId,
                authenticatedProfileId(jwt),
                request.body()));
    }

    @GetMapping
    public MessagePageResponse list(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {
        int pageOffset = offset == null ? properties.defaultOffset() : offset;
        int pageLimit = limit == null ? properties.defaultLimit() : limit;
        if (pageOffset < 0 || pageOffset > properties.maxOffset()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
        if (pageLimit < 1 || pageLimit > properties.maxLimit()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }

        MessagePage page = listConversationMessages.execute(
                conversationId,
                authenticatedProfileId(jwt),
                pageOffset,
                pageLimit);
        return MessagePageResponse.from(page, pageOffset, pageLimit);
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
