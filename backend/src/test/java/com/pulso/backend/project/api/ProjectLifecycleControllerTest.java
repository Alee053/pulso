package com.pulso.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.project.application.ChangeProjectLifecycle;
import com.pulso.backend.project.application.ProjectLifecycleRepository;
import com.pulso.backend.project.domain.ProjectLifecycleActor;
import com.pulso.backend.project.domain.ProjectLifecycleSnapshot;
import com.pulso.backend.project.domain.ProjectLifecycleState;

class ProjectLifecycleControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final Instant ARCHIVED_AT = Instant.parse("2026-09-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(ARCHIVED_AT, ZoneOffset.UTC);

    @Test
    void returnsOnlyTheMinimalPublicationRequestResultForTheJwtProfile() throws Exception {
        CapturingRepository repository = repository("OWNER", "ACTIVE", "DRAFT");

        mockMvc(repository).perform(post(path("publication-request")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.projectName").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.reviewNote").doesNotExist());

        assertThat(repository.requestedProjectId).isEqualTo(PROJECT_ID);
        assertThat(repository.requestedProfileId).isEqualTo(PROFILE_ID);
        assertThat(repository.updatedState.status()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void allowsAnActiveAdminToArchiveAndReturnsNoPrivateProjectData() throws Exception {
        CapturingRepository repository = repository("ADMIN", "ACTIVE", "PUBLISHED");

        mockMvc(repository).perform(post(path("archive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.reviewedByProfileId").doesNotExist());

        assertThat(repository.updatedState.archivedAt()).isEqualTo(ARCHIVED_AT);
    }

    @ParameterizedTest
    @CsvSource({
            "MEMBER, ACTIVE",
            "OWNER, INVITED",
            "ADMIN, LEFT",
            "OWNER, REMOVED"
    })
    void rejectsNonActiveOwnerOrAdminMemberships(String role, String membershipStatus)
            throws Exception {
        CapturingRepository repository = repository(role, membershipStatus, "DRAFT");

        mockMvc(repository).perform(post(path("publication-request")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Membership permission"))
                .andExpect(jsonPath("$.detail")
                        .value("active OWNER or ADMIN membership is required."));

        assertThat(repository.updatedState).isNull();
    }

    @Test
    void rejectsAnExternalUserAndAnOtherProjectSnapshotWithTheSameAuthorizationResponse()
            throws Exception {
        CapturingRepository externalRepository = new CapturingRepository(null);
        mockMvc(externalRepository).perform(post(path("publication-request")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Membership permission"))
                .andExpect(jsonPath("$.detail")
                        .value("active OWNER or ADMIN membership is required."));

        CapturingRepository wrongProjectRepository = new CapturingRepository(
                snapshot(OTHER_PROJECT_ID, PROFILE_ID, "DRAFT", "OWNER", "ACTIVE"));
        mockMvc(wrongProjectRepository).perform(post(path("publication-request")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Membership permission"))
                .andExpect(jsonPath("$.detail")
                        .value("active OWNER or ADMIN membership is required."));
    }

    @ParameterizedTest
    @CsvSource({
            "role, OWNER",
            "owner, true",
            "profileId, 99999999-9999-9999-9999-999999999999",
            "status, PUBLISHED",
            "reviewNote, leaked review data",
            "reviewedAt, 2026-09-20T12:00:00Z",
            "publishedAt, 2026-09-20T12:00:00Z"
    })
    void rejectsClientControlledAuthorityAndReviewFields(String field, String value)
            throws Exception {
        CapturingRepository repository = repository("OWNER", "ACTIVE", "DRAFT");
        String body = "{\"%s\":\"%s\"}".formatted(field, value);

        mockMvc(repository).perform(post(path("publication-request"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("The request body is invalid."));

        assertThat(repository.updatedState).isNull();
    }

    @Test
    void doesNotExposeADirectPublishedTransitionThroughTheActionContract() throws Exception {
        CapturingRepository repository = repository("OWNER", "ACTIVE", "DRAFT");

        mockMvc(repository).perform(post(path("publication-request"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        assertThat(repository.updatedState.status()).isNotEqualTo("PUBLISHED");
    }

    @Test
    void invalidJwtSubjectIsRejectedBeforeTheUseCaseRuns() throws Exception {
        CapturingRepository repository = repository("OWNER", "ACTIVE", "DRAFT");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ProjectLifecycleController(
                        new ChangeProjectLifecycle(repository, FIXED_CLOCK)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new InvalidJwtResolver())
                .build();

        mockMvc.perform(post(path("publication-request")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        assertThat(repository.requestedProfileId).isNull();
    }

    private static MockMvc mockMvc(CapturingRepository repository) {
        return MockMvcBuilders
                .standaloneSetup(new ProjectLifecycleController(
                        new ChangeProjectLifecycle(repository, FIXED_CLOCK)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
    }

    private static String path(String action) {
        return "/api/v1/projects/" + PROJECT_ID + "/" + action;
    }

    private static CapturingRepository repository(
            String role,
            String membershipStatus,
            String status) {
        return new CapturingRepository(
                snapshot(PROJECT_ID, PROFILE_ID, status, role, membershipStatus));
    }

    private static ProjectLifecycleSnapshot snapshot(
            UUID projectId,
            UUID profileId,
            String status,
            String role,
            String membershipStatus) {
        return new ProjectLifecycleSnapshot(
                projectId,
                profileId,
                new ProjectLifecycleState(status, null, null),
                new ProjectLifecycleActor(role, membershipStatus));
    }

    private static Jwt authenticatedJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
    }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {

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
            return authenticatedJwt();
        }
    }

    private static final class InvalidJwtResolver implements HandlerMethodArgumentResolver {

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
            return Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject("not-a-uuid")
                    .build();
        }
    }

    private static final class CapturingRepository implements ProjectLifecycleRepository {

        private final ProjectLifecycleSnapshot snapshot;
        private UUID requestedProjectId;
        private UUID requestedProfileId;
        private ProjectLifecycleState updatedState;

        private CapturingRepository(ProjectLifecycleSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<ProjectLifecycleSnapshot> findForActor(UUID projectId, UUID profileId) {
            requestedProjectId = projectId;
            requestedProfileId = profileId;
            return Optional.ofNullable(snapshot);
        }

        @Override
        public int updateLifecycle(
                UUID projectId,
                UUID profileId,
                String expectedStatus,
                ProjectLifecycleState nextState) {
            updatedState = nextState;
            return 1;
        }
    }
}
