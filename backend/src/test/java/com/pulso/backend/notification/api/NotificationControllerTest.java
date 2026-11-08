package com.pulso.backend.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pulso.backend.config.NotificationProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.notification.application.InMemoryNotificationRepository;
import com.pulso.backend.notification.application.ListNotifications;
import com.pulso.backend.notification.application.MarkNotificationRead;
import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationType;

class NotificationControllerTest {

    private static final UUID RECIPIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final NotificationProperties PROPERTIES = new NotificationProperties(0, 2, 3, 10);
    private static final String PATH = "/api/v1/me/notifications";

    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();

    @Test
    void listsOnlyTheAuthenticatedPersonsNotificationsMostRecentFirst() throws Exception {
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_REQUEST, "2026-11-08T10:00:00Z", false);
        repository.add(RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_RESPONSE, "2026-11-08T11:00:00Z", true);
        repository.add(OTHER_ID, NotificationType.NEW_MESSAGE, "2026-11-08T13:00:00Z", false);

        mockMvc(RECIPIENT_ID.toString()).perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("NEW_MESSAGE"))
                .andExpect(jsonPath("$.data[0].title").value("New message"))
                .andExpect(jsonPath("$.data[0].read").value(false))
                .andExpect(jsonPath("$.data[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.data[1].type").value("CONNECTION_RESPONSE"))
                .andExpect(jsonPath("$.data[1].read").value(true))
                .andExpect(jsonPath("$.data[1].readAt").exists())
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.hasMore").value(true))
                .andExpect(jsonPath("$.meta.unreadCount").value(2));
    }

    @Test
    void filtersByReadStatus() throws Exception {
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_REQUEST, "2026-11-08T10:00:00Z", false);
        repository.add(RECIPIENT_ID, NotificationType.CONNECTION_RESPONSE, "2026-11-08T11:00:00Z", true);

        mockMvc(RECIPIENT_ID.toString()).perform(get(PATH).param("status", "unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("CONNECTION_REQUEST"));
        mockMvc(RECIPIENT_ID.toString()).perform(get(PATH).param("status", "READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("CONNECTION_RESPONSE"));
        mockMvc(RECIPIENT_ID.toString()).perform(get(PATH).param("status", "archived"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("status must be ALL, UNREAD or READ."));
    }

    @Test
    void marksANotificationAsReadAndReturnsIt() throws Exception {
        Notification unread = repository.add(
                RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);

        mockMvc(RECIPIENT_ID.toString()).perform(post(PATH + "/" + unread.id() + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unread.id().toString()))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").exists());

        assertThat(repository.countUnread(RECIPIENT_ID)).isZero();
    }

    @Test
    void aPersonCannotMarkAnotherPersonsNotification() throws Exception {
        Notification unread = repository.add(
                RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);

        mockMvc(OTHER_ID.toString()).perform(post(PATH + "/" + unread.id() + "/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("notification_not_found"));
        mockMvc(OTHER_ID.toString()).perform(post(PATH + "/" + UUID.randomUUID() + "/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("notification_not_found"));

        assertThat(repository.countUnread(RECIPIENT_ID)).isEqualTo(1);
    }

    @Test
    void anotherPersonSeesNothingOfTheRecipientsInbox() throws Exception {
        repository.add(RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);

        mockMvc(OTHER_ID.toString()).perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.unreadCount").value(0));
    }

    @Test
    void rejectsPageWindowsOutsideTheConfiguredLimits() throws Exception {
        mockMvc(RECIPIENT_ID.toString()).perform(get(PATH).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit must be between 1 and 3."));
        mockMvc(RECIPIENT_ID.toString()).perform(get(PATH).param("limit", "4"))
                .andExpect(status().isBadRequest());
        mockMvc(RECIPIENT_ID.toString()).perform(get(PATH).param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("offset must be between 0 and 10."));
        mockMvc(RECIPIENT_ID.toString()).perform(get(PATH).param("offset", "11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnInvalidAuthenticatedSubjectAndAMalformedId() throws Exception {
        mockMvc("not-a-uuid").perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mockMvc(RECIPIENT_ID.toString()).perform(post(PATH + "/not-a-uuid/read"))
                .andExpect(status().isBadRequest());
    }

    private MockMvc mockMvc(String subject) {
        Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
        return MockMvcBuilders
                .standaloneSetup(new NotificationController(
                        new ListNotifications(repository),
                        new MarkNotificationRead(repository),
                        PROPERTIES))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver(jwt))
                .build();
    }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {

        private final Jwt jwt;

        private JwtArgumentResolver(Jwt jwt) {
            this.jwt = jwt;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return jwt;
        }
    }
}
