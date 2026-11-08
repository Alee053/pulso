package com.pulso.backend.notification.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.notification.application.InMemoryNotificationRepository;
import com.pulso.backend.notification.application.ListNotifications;
import com.pulso.backend.notification.application.MarkNotificationRead;
import com.pulso.backend.notification.application.NotificationRepository;
import com.pulso.backend.notification.domain.NotificationType;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, NotificationSecurityTest.TestBeans.class})
class NotificationSecurityTest {

    private static final UUID RECIPIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID NOTIFICATION_ID = UUID.fromString("29666666-6666-4666-8666-666666666661");
    private static final String PATH = "/api/v1/me/notifications";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessTokenToListAndToMarkAsRead() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH + "/" + NOTIFICATION_ID + "/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedPersonToListTheirNotifications() throws Exception {
        mockMvc.perform(get(PATH).with(jwt().jwt(accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    void doesNotExposeDeletingNotifications() throws Exception {
        mockMvc.perform(delete(PATH + "/" + NOTIFICATION_ID).with(jwt().jwt(accessToken())))
                .andExpect(status().is4xxClientError());
    }

    private static Jwt accessToken() {
        return Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(RECIPIENT_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        NotificationRepository notificationRepository() {
            InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
            repository.add(RECIPIENT_ID, NotificationType.NEW_MESSAGE, "2026-11-08T12:00:00Z", false);
            return repository;
        }

        @Bean
        ListNotifications listNotifications(NotificationRepository repository) {
            return new ListNotifications(repository);
        }

        @Bean
        MarkNotificationRead markNotificationRead(NotificationRepository repository) {
            return new MarkNotificationRead(repository);
        }
    }
}
