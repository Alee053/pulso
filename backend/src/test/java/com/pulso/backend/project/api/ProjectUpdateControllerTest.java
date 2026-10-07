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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
                        .value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.project.createdAt").value("2026-09-01T12:00:00Z"))
                .andExpect(jsonPath("$.project.updatedAt").value("2026-09-08T12:00:00Z"))
                .andExpect(jsonPath("$.project.reviewedByProfileId").doesNotExist())
                .andExpect(jsonPath("$.project.reviewNote").doesNotExist())
                .andExpect(jsonPath("$.project.reviewedAt").doesNotExist())
                .andExpect(jsonPath("$.project.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.project.archivedAt").doesNotExist());

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
                          "description": null,
                          "valueProposition": null,
                          "department": null
                        }
                        """))
                .andExpect(status().isOk());

        assertThat(repository.patch.description()).isNull();
        assertThat(repository.patch.descriptionProvided()).isTrue();
        assertThat(repository.patch.valueProposition()).isNull();
        assertThat(repository.patch.valuePropositionProvided()).isTrue();
        assertThat(repository.patch.department()).isNull();
        assertThat(repository.patch.departmentProvided()).isTrue();
        assertThat(repository.patch.nameProvided()).isFalse();
        assertThat(repository.patch.cityProvided()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "id,aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "slug,other-project",
            "createdByProfileId,aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "createdAt,2026-09-08T12:00:00Z",
            "updatedAt,2026-09-08T12:00:00Z",
            "status,PUBLISHED",
            "verificationStatus,VERIFIED",
            "reviewedByProfileId,aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "reviewNote,Changed review note",
            "reviewedAt,2026-09-08T12:00:00Z",
            "publishedAt,2026-09-08T12:00:00Z",
            "archivedAt,2026-09-08T12:00:00Z",
            "profileId,aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "projectId,aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "membershipStatus,ACTIVE",
            "unknownField,unexpected",
            "created_by_profile_id,aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "updated_at,2026-09-08T12:00:00Z"
    })
    void rejectsProtectedOrUnknownFieldsAtTheHttpBoundary(String field, String value) throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch(
                "/api/v1/projects/{projectId}",
                PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No debe persistir\",\"%s\":\"%s\"}".formatted(field, value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(ApiMessages.INVALID_REQUEST_TITLE));

        assertThat(repository.patch).isNull();
    }

    @Test
    void rejectsAClientSuppliedRoleBeforePersistence() throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "No debe persistir",
                          "role": "OWNER"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(ApiMessages.INVALID_REQUEST_TITLE));

        assertThat(repository.patch).isNull();
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

    @ParameterizedTest
    @ValueSource(strings = { "name", "tagline", "stage", "sectorId", "city" })
    void rejectsExplicitNullForRequiredFieldsBeforePersistence(String field) throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"%s\":null}".formatted(field)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid project"))
                .andExpect(jsonPath("$.detail").value(field + " is required."));

        assertThat(repository.patch).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "name", "tagline", "stage", "city" })
    void rejectsBlankRequiredTextBeforePersistence(String field) throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"%s\":\"  \"}".formatted(field)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(field + " must not be blank."));

        assertThat(repository.patch).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "name,160", "tagline,240", "description,5000", "valueProposition,5000",
            "city,120", "department,120", "stage,40"
    })
    void rejectsOverlongFieldsBeforePersistence(String field, int maxLength) throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"%s\":\"%s\"}".formatted(field, "x".repeat(maxLength + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value(field + " must not exceed " + maxLength + " characters."));

        assertThat(repository.patch).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "not-a-uuid,22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333,not-a-uuid"
    })
    void rejectsMalformedIdentifiersBeforePersistence(String projectId, String sectorId) throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectorId\":\"%s\"}".formatted(sectorId)))
                .andExpect(status().isBadRequest());

        assertThat(repository.patch).isNull();
    }

    @Test
    void rejectsAJsonNullBodyBeforePersistence() throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value(ApiMessages.INVALID_REQUEST_TITLE));

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
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-09-01T12:00:00Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-09-08T12:00:00Z");
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
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "Private review note",
                createdAt.plusDays(1),
                createdAt.plusDays(2),
                createdAt,
                updatedAt,
                createdAt.plusDays(3));
    }
}
