package com.pulso.backend.connection.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

import com.pulso.backend.connection.application.AcceptConnectionIntent;
import com.pulso.backend.connection.application.ConnectionIntentAcceptanceRepository;
import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;
import com.pulso.backend.discovery.api.ApiExceptionHandler;

class ConnectionIntentAcceptanceControllerTest {

    private static final UUID INTENT_ID = UUID.fromString("27333333-3333-4333-8333-333333333333");
    private static final UUID CONVERSATION_ID = UUID.fromString("27444444-4444-4444-8444-444444444444");
    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID INITIATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESPONDER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void acceptsUsingOnlyTheJwtIdentityAndReturnsBothParticipants() throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository, authenticatedJwt()).perform(post(path()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionIntentId").value(INTENT_ID.toString()))
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.conversationType").value("PROJECT_CONTACT"))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.participants[0].profileId").value(INITIATOR_ID.toString()))
                .andExpect(jsonPath("$.participants[0].role").value("INITIATOR"))
                .andExpect(jsonPath("$.participants[1].profileId").value(RESPONDER_ID.toString()))
                .andExpect(jsonPath("$.participants[1].role").value("PROJECT_MEMBER"));

        assertThat(repository.acceptedBy).isEqualTo(RESPONDER_ID);
    }

    @Test
    void mapsBlockedProfilesToAForbiddenProblem() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        repository.blocked = true;

        mockMvc(repository, authenticatedJwt()).perform(post(path()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Connection request access denied"))
                .andExpect(jsonPath("$.code").value("connection_intent_forbidden"));
    }

    @Test
    void rejectsAnInvalidAuthenticatedSubject() throws Exception {
        Jwt invalidJwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject("not-a-uuid")
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc(new CapturingRepository(), invalidJwt).perform(post(path()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    private static String path() {
        return "/api/v1/connection-intents/" + INTENT_ID + "/accept";
    }

    private static MockMvc mockMvc(CapturingRepository repository, Jwt jwt) {
        return MockMvcBuilders
                .standaloneSetup(new ConnectionIntentAcceptanceController(
                        new AcceptConnectionIntent(repository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver(jwt))
                .build();
    }

    private static Jwt authenticatedJwt() {
        return Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(RESPONDER_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
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

    private static final class CapturingRepository implements ConnectionIntentAcceptanceRepository {

        private boolean blocked;
        private UUID acceptedBy;

        @Override
        public Optional<ConnectionIntent> findByIdForUpdate(UUID connectionIntentId) {
            return Optional.of(ConnectionIntent.restored(
                    INTENT_ID,
                    INITIATOR_ID,
                    PROJECT_ID,
                    null,
                    ConnectionIntentReason.COLLABORATE,
                    null,
                    ConnectionIntent.Status.PENDING.name(),
                    null,
                    OffsetDateTime.parse("2026-10-30T20:00:00Z")));
        }

        @Override
        public boolean hasActiveProjectMembership(UUID projectId, UUID profileId) {
            return true;
        }

        @Override
        public boolean profilesAreBlocked(UUID firstProfileId, UUID secondProfileId) {
            return blocked;
        }

        @Override
        public Optional<AcceptedConnectionConversation> findConversation(UUID connectionIntentId) {
            return Optional.empty();
        }

        @Override
        public AcceptedConnectionConversation acceptAndCreateConversation(
                ConnectionIntent connectionIntent,
                UUID responderProfileId) {
            acceptedBy = responderProfileId;
            return new AcceptedConnectionConversation(
                    INTENT_ID,
                    CONVERSATION_ID,
                    PROJECT_ID,
                    INITIATOR_ID,
                    responderProfileId);
        }

          @Override
    public void updateStatus(ConnectionIntent connectionIntent, UUID respondedByProfileId) {
        
    }

    @Override
        public java.util.List<ConnectionIntent> findPendingByProjectMembership(java.util.UUID profileId) {
            return java.util.List.of();
        }
    }
}
