package com.pulso.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.project.application.ProjectUpdateAuthorizationException;
import com.pulso.backend.project.application.ProjectUpdateRepository;
import com.pulso.backend.project.application.UpdateProjectInformation;
import com.pulso.backend.project.domain.ProjectUpdatePatch;
import com.pulso.backend.project.domain.UpdatedProject;

class ProjectUpdateControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID SECTOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    @Test
    void updatesAllowedFieldsAndReturnsTheProjectRepresentation() throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.project.slug").value("eco-pack"))
                .andExpect(jsonPath("$.project.name").value("EcoPack actualizado"))
                .andExpect(jsonPath("$.project.tagline").value("Envases nuevos"))
                .andExpect(jsonPath("$.project.description").value("Descripción nueva"))
                .andExpect(jsonPath("$.project.valueProposition").value("Menos residuos"))
                .andExpect(jsonPath("$.project.sectorId").value(SECTOR_ID.toString()))
                .andExpect(jsonPath("$.project.stage").value("MVP"))
                .andExpect(jsonPath("$.project.city").value("Cochabamba"))
                .andExpect(jsonPath("$.project.department").value("Cochabamba"))
                .andExpect(jsonPath("$.project.status").value("DRAFT"))
                .andExpect(jsonPath("$.project.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.project.createdByProfileId")
                        .value(PROFILE_ID.toString()));

        assertThat(repository.projectId).isEqualTo(PROJECT_ID);
        assertThat(repository.authenticatedProfileId).isEqualTo(PROFILE_ID);
        assertThat(repository.patch.name()).isEqualTo("EcoPack actualizado");
        assertThat(repository.patch.valueProposition()).isEqualTo("Menos residuos");
    }

    @Test
    void preservesOmittedFieldsByPassingNullToThePatchPort() throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Solo nombre"
                        }
                        """))
                .andExpect(status().isOk());

        assertThat(repository.patch.name()).isEqualTo("Solo nombre");
        assertThat(repository.patch.tagline()).isNull();
        assertThat(repository.patch.description()).isNull();
        assertThat(repository.patch.sectorId()).isNull();
        assertThat(repository.patch.taglineProvided()).isFalse();
    }

    @Test
    void preservesPresenceForExplicitNullableValues() throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "description": null
                        }
                        """))
                .andExpect(status().isOk());

        assertThat(repository.patch.description()).isNull();
        assertThat(repository.patch.descriptionProvided()).isTrue();
    }

    @Test
    void rejectsProtectedOrUnknownFieldsAtTheHttpBoundary() throws Exception {
        mockMvc(new CapturingRepository()).perform(patch(
                "/api/v1/projects/{projectId}",
                PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "status": "PUBLISHED"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(ApiMessages.INVALID_REQUEST_TITLE));
    }

    @Test
    void rejectsInvalidValuesBeforePersistence() throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "stage": "LAUNCHED"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid project"));

        assertThat(repository.patch).isNull();
    }

    @Test
    void rejectsInactiveSectorsBeforePersistence() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        repository.activeSector = false;

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "sectorId": "22222222-2222-2222-2222-222222222222"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(ApiMessages.SECTOR_MUST_BE_ACTIVE));

        assertThat(repository.patch).isNull();
    }

    @Test
    void returnsTheStableAuthorizationErrorForMissingMembershipOrProject() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        repository.result = Optional.empty();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Membership permission"))
                .andExpect(jsonPath("$.detail")
                        .value("active OWNER or ADMIN membership is required."));
    }

    private static MockMvc mockMvc(ProjectUpdateRepository repository) {
        return MockMvcBuilders
                .standaloneSetup(new ProjectUpdateController(new UpdateProjectInformation(
                        repository,
                        new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254))))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
    }

    private static String validRequest() {
        return """
                {
                  "name": "EcoPack actualizado",
                  "tagline": "Envases nuevos",
                  "description": "Descripción nueva",
                  "valueProposition": "Menos residuos",
                  "stage": "mvp",
                  "sectorId": "%s",
                  "city": "Cochabamba",
                  "department": "Cochabamba"
                }
                """.formatted(SECTOR_ID);
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

    private static final class CapturingRepository implements ProjectUpdateRepository {

        private boolean activeSector = true;
        private Optional<UpdatedProject> result = Optional.of(project());
        private UUID projectId;
        private UUID authenticatedProfileId;
        private ProjectUpdatePatch patch;

        @Override
        public boolean hasActiveSector(UUID sectorId) {
            return activeSector;
        }

        @Override
        public Optional<UpdatedProject> updateInformation(
                UUID projectId,
                UUID authenticatedProfileId,
                ProjectUpdatePatch patch) {
            this.projectId = projectId;
            this.authenticatedProfileId = authenticatedProfileId;
            this.patch = patch;
            return result;
        }
    }

    private static UpdatedProject project() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-08T12:00:00Z");
        return new UpdatedProject(
                PROJECT_ID,
                "eco-pack",
                "EcoPack actualizado",
                "Envases nuevos",
                "Descripción nueva",
                "Menos residuos",
                SECTOR_ID,
                "MVP",
                "Cochabamba",
                "Cochabamba",
                "DRAFT",
                "UNVERIFIED",
                PROFILE_ID,
                null,
                null,
                null,
                null,
                timestamp,
                timestamp,
                null);
    }
}
