package com.pulso.backend.opportunity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

import com.pulso.backend.config.ProjectOpportunityProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.opportunity.application.ManageProjectOpportunities;
import com.pulso.backend.opportunity.application.ProjectOpportunityRepository;
import com.pulso.backend.opportunity.domain.OpportunityActor;
import com.pulso.backend.opportunity.domain.OpportunityContent;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;

class ProjectOpportunityControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SECTOR_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final UUID OPPORTUNITY_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-01T12:00:00Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-10-02T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsAnOpportunityAndReturnsOnlyTheManagedResponseFields() throws Exception {
        RecordingRepository repository = repository("OWNER", "ACTIVE");

        mockMvc(repository).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateRequest("DRAFT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(OPPORTUNITY_ID.toString()))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.opportunityType").value("TALENT"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.createdByProfileId").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());

        assertThat(repository.createdByProfileId).isEqualTo(PROFILE_ID);
        assertThat(repository.created).isNotNull();
    }

    @Test
    void listsOpportunitiesWithBoundedPaginationMetadata() throws Exception {
        RecordingRepository repository = repository("ADMIN", "ACTIVE");
        repository.opportunities.add(opportunity("OPEN"));

        mockMvc(repository).perform(get(path()).param("offset", "0").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
    }

    @Test
    void editsContentAndChangesStatusThroughSeparateContracts() throws Exception {
        RecordingRepository repository = repository("OWNER", "ACTIVE");
        repository.opportunities.add(opportunity("DRAFT"));
        MockMvc mockMvc = mockMvc(repository);

        mockMvc.perform(put(path() + "/" + OPPORTUNITY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEditRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post(path() + "/" + OPPORTUNITY_ID + "/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(repository.opportunities.getFirst().createdByProfileId()).isEqualTo(PROFILE_ID);
        assertThat(repository.opportunities.getFirst().createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void returnsSafeErrorsForInvalidInputInvalidTransitionsAndMissingResources() throws Exception {
        RecordingRepository repository = repository("OWNER", "ACTIVE");
        MockMvc mockMvc = mockMvc(repository);

        mockMvc.perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"opportunityType":"TALENT","title":"Role","description":"Details","remoteAllowed":false}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid opportunity"));

        mockMvc.perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateRequest("PAUSED")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(path() + "/" + OPPORTUNITY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEditRequest().replace("}", ",\"status\":\"OPEN\"}")))
                .andExpect(status().isBadRequest());

        repository.opportunities.add(opportunity("DRAFT"));
        mockMvc.perform(post(path() + "/" + OPPORTUNITY_ID + "/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PAUSE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid opportunity transition"));

        mockMvc.perform(put(path() + "/99999999-9999-9999-9999-999999999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEditRequest()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAProjectMemberWithoutManagementPermission() throws Exception {
        RecordingRepository repository = repository("MEMBER", "ACTIVE");

        mockMvc(repository).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateRequest("DRAFT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Membership permission"));

        assertThat(repository.created).isNull();
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/opportunities";
    }

    private static String validCreateRequest(String status) {
        return """
                {
                  "opportunityType": "TALENT",
                  "title": "Backend role",
                  "description": "Help us deliver the next release.",
                  "city": "La Paz",
                  "department": "La Paz",
                  "remoteAllowed": false,
                  "status": "%s"
                }
                """.formatted(status);
    }

    private static String validEditRequest() {
        return """
                {
                  "opportunityType": "CUSTOMER",
                  "title": "Updated title",
                  "description": "Updated description.",
                  "city": "Santa Cruz",
                  "department": "Santa Cruz",
                  "remoteAllowed": true
                }
                """;
    }

    private static MockMvc mockMvc(RecordingRepository repository) {
        ManageProjectOpportunities useCase = new ManageProjectOpportunities(
                repository, new ProjectOpportunityProperties(0, 20, 50, 100_000), CLOCK);
        return MockMvcBuilders.standaloneSetup(new ProjectOpportunityController(
                        useCase, new ProjectOpportunityProperties(0, 20, 50, 100_000)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver(false))
                .build();
    }

    private static RecordingRepository repository(String role, String membershipStatus) {
        return new RecordingRepository(role, membershipStatus);
    }

    private static ProjectOpportunity opportunity(String status) {
        return new ProjectOpportunity(
                OPPORTUNITY_ID,
                PROJECT_ID,
                "TALENT",
                "Backend role",
                "Help us deliver the next release.",
                status,
                "La Paz",
                "La Paz",
                false,
                PROFILE_ID,
                null,
                null,
                null,
                CREATED_AT,
                UPDATED_AT);
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
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject(invalidSubject ? "not-a-uuid" : PROFILE_ID.toString())
                    .claim("role", "authenticated")
                    .audience(List.of("authenticated"))
                    .build();
        }
    }

    private static final class RecordingRepository implements ProjectOpportunityRepository {

        private String role;
        private String membershipStatus;
        private UUID createdByProfileId;
        private OpportunityContent created;
        private final List<ProjectOpportunity> opportunities = new ArrayList<>();

        private RecordingRepository(String role, String membershipStatus) {
            this.role = role;
            this.membershipStatus = membershipStatus;
        }

        @Override
        public Optional<OpportunityActor> lockActor(UUID projectId, UUID profileId) {
            if (!PROJECT_ID.equals(projectId) || !PROFILE_ID.equals(profileId)) {
                return Optional.empty();
            }
            return Optional.of(new OpportunityActor(
                    PROJECT_ID, PROFILE_ID, "PUBLISHED", SECTOR_ID, role, membershipStatus));
        }

        @Override
        public boolean lockActiveSector(UUID sectorId) {
            return SECTOR_ID.equals(sectorId);
        }

        @Override
        public List<ProjectOpportunity> list(UUID projectId, int offset, int limit) {
            return opportunities.stream()
                    .filter(opportunity -> projectId.equals(opportunity.projectId()))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<ProjectOpportunity> find(UUID projectId, UUID opportunityId) {
            return opportunities.stream()
                    .filter(opportunity -> projectId.equals(opportunity.projectId()))
                    .filter(opportunity -> opportunityId.equals(opportunity.id()))
                    .findFirst();
        }

        @Override
        public Optional<ProjectOpportunity> create(
                UUID projectId,
                UUID profileId,
                OpportunityContent content,
                String status) {
            created = content;
            createdByProfileId = profileId;
            ProjectOpportunity opportunity = new ProjectOpportunity(
                    OPPORTUNITY_ID, projectId, content.opportunityType(), content.title(), content.description(),
                    status, content.city(), content.department(), content.remoteAllowed(), profileId,
                    null, null, null, CREATED_AT, UPDATED_AT);
            opportunities.add(opportunity);
            return Optional.of(opportunity);
        }

        @Override
        public Optional<ProjectOpportunity> updateContent(
                UUID projectId,
                UUID opportunityId,
                UUID profileId,
                OffsetDateTime expectedUpdatedAt,
                OpportunityContent content) {
            return find(projectId, opportunityId).map(current -> replace(
                    current, content.opportunityType(), content.title(), content.description(),
                    content.city(), content.department(), content.remoteAllowed(), current.status(), null));
        }

        @Override
        public Optional<ProjectOpportunity> updateStatus(
                UUID projectId,
                UUID opportunityId,
                UUID profileId,
                String expectedStatus,
                OffsetDateTime expectedUpdatedAt,
                String nextStatus,
                OffsetDateTime closedAt) {
            return find(projectId, opportunityId).map(current -> replace(
                    current, current.opportunityType(), current.title(), current.description(),
                    current.city(), current.department(), current.remoteAllowed(), nextStatus, closedAt));
        }

        private ProjectOpportunity replace(
                ProjectOpportunity current,
                String type,
                String title,
                String description,
                String city,
                String department,
                boolean remoteAllowed,
                String status,
                OffsetDateTime closedAt) {
            ProjectOpportunity updated = new ProjectOpportunity(
                    current.id(), current.projectId(), type, title, description, status, city, department,
                    remoteAllowed, current.createdByProfileId(), current.startsAt(), current.endsAt(),
                    closedAt == null ? current.closedAt() : closedAt, current.createdAt(), UPDATED_AT.plusSeconds(1));
            opportunities.replaceAll(value -> value.id().equals(updated.id()) ? updated : value);
            return updated;
        }
    }
}
