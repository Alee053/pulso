package com.pulso.backend.connection.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
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
import com.pulso.backend.connection.application.AcceptConnectionIntent;
import com.pulso.backend.connection.application.ConnectionIntentAcceptanceRepository;
import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.connection.domain.ConnectionIntent;

@WebMvcTest(ConnectionIntentAcceptanceController.class)
@Import({SecurityConfig.class, ConnectionIntentAcceptanceSecurityTest.TestBeans.class})
class ConnectionIntentAcceptanceSecurityTest {

    private static final UUID INTENT_ID = UUID.fromString("27333333-3333-4333-8333-333333333333");
    private static final UUID CONVERSATION_ID = UUID.fromString("27444444-4444-4444-8444-444444444444");
    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID INITIATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESPONDER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessToken() throws Exception {
        mockMvc.perform(post(path())).andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedUser() throws Exception {
        Jwt accessToken = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(RESPONDER_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(post(path()).with(jwt().jwt(accessToken)))
                .andExpect(status().isOk());
    }

    private static String path() {
        return "/api/v1/connection-intents/" + INTENT_ID + "/accept";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        ConnectionIntentAcceptanceRepository connectionIntentAcceptanceRepository() {
            return new ConnectionIntentAcceptanceRepository() {
                @Override
                public Optional<ConnectionIntent> findByIdForUpdate(UUID connectionIntentId) {
                    return Optional.of(new ConnectionIntent(
                            INTENT_ID,
                            INITIATOR_ID,
                            PROJECT_ID,
                            ConnectionIntent.Status.PENDING,
                            null));
                }

                @Override
                public boolean hasActiveProjectMembership(UUID projectId, UUID profileId) {
                    return true;
                }

                @Override
                public boolean profilesAreBlocked(UUID firstProfileId, UUID secondProfileId) {
                    return false;
                }

                @Override
                public Optional<AcceptedConnectionConversation> findConversation(UUID connectionIntentId) {
                    return Optional.empty();
                }

                @Override
                public AcceptedConnectionConversation acceptAndCreateConversation(
                        ConnectionIntent connectionIntent,
                        UUID responderProfileId) {
                    return new AcceptedConnectionConversation(
                            INTENT_ID,
                            CONVERSATION_ID,
                            PROJECT_ID,
                            INITIATOR_ID,
                            responderProfileId);
                }
            };
        }

        @Bean
        AcceptConnectionIntent acceptConnectionIntent(ConnectionIntentAcceptanceRepository repository) {
            return new AcceptConnectionIntent(repository);
        }
    }
}
