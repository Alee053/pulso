package com.pulso.backend.notification.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.NotificationProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.notification.application.ListNotifications;
import com.pulso.backend.notification.application.MarkNotificationRead;
import com.pulso.backend.notification.application.NotificationPage;
import com.pulso.backend.notification.domain.NotificationReadFilter;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final ListNotifications listNotifications;
    private final MarkNotificationRead markNotificationRead;
    private final NotificationProperties properties;

    public NotificationController(
            ListNotifications listNotifications,
            MarkNotificationRead markNotificationRead,
            NotificationProperties properties) {
        this.listNotifications = listNotifications;
        this.markNotificationRead = markNotificationRead;
        this.properties = properties;
    }

    @GetMapping
    public NotificationPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
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

        NotificationPage page = listNotifications.execute(
                authenticatedProfileId(jwt),
                NotificationReadFilter.parse(status),
                pageOffset,
                pageLimit);
        return NotificationPageResponse.from(page, pageOffset, pageLimit);
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal Jwt jwt) {
        return NotificationResponse.from(
                markNotificationRead.execute(notificationId, authenticatedProfileId(jwt)));
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
