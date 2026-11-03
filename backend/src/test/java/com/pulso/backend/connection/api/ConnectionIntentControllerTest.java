package com.pulso.backend.connection.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

import com.pulso.backend.connection.application.ConnectionIntentRejectedException;
import com.pulso.backend.connection.application.ConnectionIntentRepository;
import com.pulso.backend.connection.application.CreateConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;

class ConnectionIntentControllerTest {

    private static final UUID PROJECT_ID = id("22222222-2222-2222-2222-222222222222");
    private static final UUID SENDER_ID = id("11111111-1111-1111-1111-111111111111");
    private static final UUID OPPORTUNITY_ID = id("33333333-3333-3333-3333-333333333333");
    private static final UUID INTENT_ID = id("44444444-4444-4444-4444-444444444444");
    private static final String PATH = "/api/v1/projects/" + PROJECT_ID + "/connection-intents";

    @Test
    void createsPendingConnectionIntentAndReturnsOnlyTheRequestContract() throws Exception {
        RecordingRepository repository = new RecordingRepository();

        mockMvc(repository, false).perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(INTENT_ID.toString()))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.opportunityId").value(OPPORTUNITY_ID.toString()))
                .andExpect(jsonPath("$.senderProfileId").value(SENDER_ID.toString()))
                .andExpect(jsonPath("$.reason").value("COLLABORATE"))
                .andExpect(jsonPath("$.message").value("Me interesa colaborar."))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.fromProfileId").doesNotExist())
                .andExpect(jsonPath("$.respondedByProfileId").doesNotExist())
                .andExpect(jsonPath("$.outcomeType").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(repository.callCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(repository.senderId).isEqualTo(SENDER_ID);
        org.assertj.core.api.Assertions.assertThat(repository.projectId).isEqualTo(PROJECT_ID);
        org.assertj.core.api.Assertions.assertThat(repository.opportunityId).isEqualTo(OPPORTUNITY_ID);
    }

    @Test
    void rejectsMissingBodyAndInvalidReasonWithSafeBadRequestProblems() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        MockMvc mockMvc = mockMvc(repository, false);

        mockMvc.perform(post(PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        org.assertj.core.api.Assertions.assertThat(repository.callCount).isZero();
    }

    @Test
    void rejectsClientSuppliedSenderAndStatusFieldsBeforeRepositoryAccess() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        MockMvc mockMvc = mockMvc(repository, false);

        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\",\"senderProfileId\":\""
                        + id("99999999-9999-9999-9999-999999999999") + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\",\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(repository.callCount).isZero();
    }

    @Test
    void returnsTheSameSafeNotFoundResponseForUnavailableProjectOrOpportunity() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.failure = new ConnectionIntentRejectedException(
                ConnectionIntentRejectedException.Reason.CONTEXT_UNAVAILABLE);

        mockMvc(repository, false).perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Connection context unavailable"))
                .andExpect(jsonPath("$.detail").value(
                        "The project or opportunity does not exist or cannot receive requests."))
                .andExpect(jsonPath("$.code").value("connection_context_unavailable"));
    }

    @Test
    void rejectsInactiveProfilesWithoutExposingAccountDetails() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.failure = new ConnectionIntentRejectedException(
                ConnectionIntentRejectedException.Reason.PROFILE_INACTIVE);

        mockMvc(repository, false).perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Connection request not permitted"))
                .andExpect(jsonPath("$.detail").value(
                        "The authenticated profile cannot create connection requests."))
                .andExpect(jsonPath("$.code").value("connection_intent_forbidden"))
                .andExpect(jsonPath("$.accountStatus").doesNotExist());
    }

    @Test
    void reportsPendingDuplicateAsConflict() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.failure = new ConnectionIntentRejectedException(
                ConnectionIntentRejectedException.Reason.DUPLICATE_PENDING);

        mockMvc(repository, false).perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Pending connection request already exists"))
                .andExpect(jsonPath("$.code").value("connection_intent_duplicate"));
    }

    @Test
    void returnsUnauthorizedWhenTheAuthenticatedSubjectIsNotAProfileUuid() throws Exception {
        mockMvc(new RecordingRepository(), true).perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    private static MockMvc mockMvc(RecordingRepository repository, boolean invalidSubject) {
        return MockMvcBuilders.standaloneSetup(new ConnectionIntentController(
                        new CreateConnectionIntent(repository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver(invalidSubject))
                .build();
    }

    private static String validRequest() {
        return """
                {
                  "reason": "COLLABORATE",
                  "message": "Me interesa colaborar.",
                  "opportunityId": "%s"
                }
                """.formatted(OPPORTUNITY_ID);
    }

    private static UUID id(String value) {
        return UUID.fromString(value);
    }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {

        private final boolean invalidSubject;

        private JwtArgumentResolver(boolean invalidSubject) {
            this.invalidSubject = invalidSubject;
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
                WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject(invalidSubject ? "not-a-profile-uuid" : SENDER_ID.toString())
                    .claim("role", "authenticated")
                    .build();
        }
    }

    private static final class RecordingRepository implements ConnectionIntentRepository {

        private int callCount;
        private UUID senderId;
        private UUID projectId;
        private UUID opportunityId;
        private RuntimeException failure;

        @Override
        public ConnectionIntent createPending(
                UUID senderProfileId,
                UUID targetProjectId,
                UUID targetOpportunityId,
                ConnectionIntentReason reason,
                String message) {
            callCount++;
            senderId = senderProfileId;
            projectId = targetProjectId;
            opportunityId = targetOpportunityId;
            if (failure != null) {
                throw failure;
            }
            return ConnectionIntent.pending(
                    INTENT_ID,
                    senderProfileId,
                    targetProjectId,
                    targetOpportunityId,
                    reason,
                    message,
                    OffsetDateTime.parse("2026-11-03T12:00:00Z"));
        }
    }
}
