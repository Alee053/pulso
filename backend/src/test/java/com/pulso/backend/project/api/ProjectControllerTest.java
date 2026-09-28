package com.pulso.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.project.application.CreateDraftProject;
import com.pulso.backend.project.application.ProjectConflictException;
import com.pulso.backend.project.application.ProjectRepository;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;

class ProjectControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final UUID SECTOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID MEMBER_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");

    @Test
    void createsAProjectAndReturnsItsOwnerRelation() throws Exception {
        CapturingRepository repository = new CapturingRepository();

        mockMvc(repository).perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project.id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.project.slug").value("eco-pack"))
                .andExpect(jsonPath("$.project.name").value("EcoPack Bolivia"))
                .andExpect(jsonPath("$.project.tagline").value("Envases sostenibles"))
                .andExpect(jsonPath("$.project.description").value("Proyecto de prueba."))
                .andExpect(jsonPath("$.project.sectorId").value(SECTOR_ID.toString()))
                .andExpect(jsonPath("$.project.city").value("Cochabamba"))
                .andExpect(jsonPath("$.project.stage").value("MVP"))
                .andExpect(jsonPath("$.project.status").value("DRAFT"))
                .andExpect(jsonPath("$.project.createdByProfileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.owner.id").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$.owner.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.owner.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.owner.role").value("OWNER"))
                .andExpect(jsonPath("$.owner.membershipStatus").value("ACTIVE"));

        assertThat(repository.createdByProfileId).isEqualTo(PROFILE_ID);
        assertThat(repository.savedDraft.sectorId()).isEqualTo(SECTOR_ID);
        assertThat(repository.savedDraft.stage()).isEqualTo("MVP");
    }

    @Test
    void rejectsAnUnknownOrInactiveSectorBeforePersistence() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        repository.activeSector = false;

        mockMvc(repository).perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid project"))
                .andExpect(jsonPath("$.detail")
                        .value("sectorId must reference an active sector."));

        assertThat(repository.savedDraft).isNull();
    }

    @Test
    void rejectsClientProvidedProjectIdentity() throws Exception {
        mockMvc(new CapturingRepository()).perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest().replace(
                        "\"stage\": \"mvp\"",
                        "\"stage\": \"mvp\",\n  \"createdByProfileId\": \""
                                + OTHER_PROFILE_ID + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void translatesAProjectSlugConflictToConflictResponse() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        repository.createFailure = new ProjectConflictException();

        mockMvc(repository).perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Project conflict"))
                .andExpect(jsonPath("$.detail")
                        .value("A project with the same slug already exists."));
    }

    @Test
    void sanitizesPersistenceFailures() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        repository.createFailure = new DataAccessResourceFailureException(
                "password=secret host=database");

        mockMvc(repository).perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Data store unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail")
                        .value("The data store is temporarily unavailable."));
    }

    @Test
    void returnsAProblemForInvalidProjectValues() throws Exception {
        mockMvc(new CapturingRepository()).perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest().replace(
                        "\"name\": \"EcoPack Bolivia\"",
                        "\"name\": \" \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid project"))
                .andExpect(jsonPath("$.detail").value("name must not be blank."));
    }

    private static MockMvc mockMvc(ProjectRepository repository) {
        return MockMvcBuilders
                .standaloneSetup(new ProjectController(new CreateDraftProject(
                        repository,
                        new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254))))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
    }

    private static String validRequest() {
        return """
                {
                  "slug": "Eco-Pack",
                  "name": "EcoPack Bolivia",
                  "tagline": "Envases sostenibles",
                  "description": "Proyecto de prueba.",
                  "sectorId": "%s",
                  "city": "Cochabamba",
                  "stage": "mvp"
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

    private static final class CapturingRepository implements ProjectRepository {

        private boolean activeSector = true;
        private RuntimeException createFailure;
        private ProjectDraft savedDraft;
        private UUID createdByProfileId;

        @Override
        public boolean hasActiveSector(UUID sectorId) {
            return activeSector;
        }

        @Override
        public Project createDraft(ProjectDraft draft, UUID createdByProfileId) {
            savedDraft = draft;
            this.createdByProfileId = createdByProfileId;
            if (createFailure != null) {
                throw createFailure;
            }
            return new Project(
                    PROJECT_ID,
                    draft.slug(),
                    draft.name(),
                    draft.tagline(),
                    draft.description(),
                    draft.sectorId(),
                    draft.city(),
                    draft.stage(),
                    "DRAFT",
                    createdByProfileId);
        }

        @Override
        public ProjectMember createOwner(UUID projectId, UUID profileId) {
            return new ProjectMember(
                    MEMBER_ID,
                    projectId,
                    profileId,
                    "OWNER",
                    "ACTIVE",
                    profileId);
        }
    }
}
