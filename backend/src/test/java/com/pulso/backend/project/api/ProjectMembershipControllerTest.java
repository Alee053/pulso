package com.pulso.backend.project.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.project.application.AdministerProjectMembership;
import com.pulso.backend.project.application.ListProjectMemberships;
import com.pulso.backend.project.application.MembershipAdministrationRepository;
import com.pulso.backend.config.ProjectMembershipsProperties;
import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.ManagedProjectMember;
import com.pulso.backend.project.domain.ProjectMember;

class ProjectMembershipControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID MEMBER_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID TARGET_PROFILE_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void updatesMembershipAndReturnsTheResultingRow() throws Exception {
        ProjectMember target = member("MEMBER", "ACTIVE");
        CapturingRepository repository = repository(target);

        mockMvc(repository).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "action": "changeRole",
                          "role": "ADMIN"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.profileId").value(TARGET_PROFILE_ID.toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.createdByProfileId").value(PROFILE_ID.toString()));
    }

    @Test
    void listsMemberSummariesWithBoundedPaginationAndNoInvitationSecrets() throws Exception {
        CapturingRepository repository = repository(member("MEMBER", "ACTIVE"));
        repository.managedMembers = List.of(
                new ManagedProjectMember(MEMBER_ID, PROJECT_ID, TARGET_PROFILE_ID,
                        "Ada", null, "MEMBER", "ACTIVE"),
                new ManagedProjectMember(UUID.fromString("77777777-7777-7777-7777-777777777777"),
                        PROJECT_ID, null, null, "ada@example.test", "MEMBER", "INVITED"));

        mockMvc(repository).perform(get("/api/v1/projects/" + PROJECT_ID + "/members")
                .param("offset", "0")
                .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].displayName").value("Ada"))
                .andExpect(jsonPath("$.data[0].inviteeEmail").doesNotExist())
                .andExpect(jsonPath("$.data[0].inviteTokenDigest").doesNotExist())
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.hasMore").value(true));
    }

    @Test
    void rejectsOutOfRangeRosterPaginationBeforeReadingMemberships() throws Exception {
        CapturingRepository repository = repository(member("MEMBER", "ACTIVE"));

        mockMvc(repository).perform(get("/api/v1/projects/" + PROJECT_ID + "/members")
                .param("offset", "100001")
                .param("limit", "20"))
                .andExpect(status().isBadRequest());

        assertEquals(0, repository.rosterReads);
    }

    @Test
    void mapsPermissionFailuresToForbiddenProblemDetails() throws Exception {
        CapturingRepository repository = repository(member("MEMBER", "ACTIVE"));
        repository.actor = null;

        mockMvc(repository).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"withdraw\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Membership permission"))
                .andExpect(jsonPath("$.code").value("membership_permission"))
                .andExpect(jsonPath("$.detail")
                        .value("active OWNER or ADMIN membership is required."));

        assertEquals(0, repository.updateCalls);
        assertEquals("ACTIVE", repository.target.membershipStatus());
    }

    @Test
    void mapsInvalidTransitionsToConflictProblemDetails() throws Exception {
        ProjectMember target = member("MEMBER", "LEFT");
        CapturingRepository repository = repository(target);

        mockMvc(repository).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"changeRole\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid membership transition"))
                .andExpect(jsonPath("$.code").value("membership_invalid_transition"));

        assertEquals(0, repository.updateCalls);
        assertEquals(target, repository.target);
    }

    @ParameterizedTest
    @CsvSource({"activate,REMOVED,ACTIVE", "withdraw,ACTIVE,REMOVED"})
    void appliesStatusActionsWithoutChangingIdentityOrRole(
            String action, String previousStatus, String nextStatus) throws Exception {
        ProjectMember target = member("MEMBER", previousStatus);
        CapturingRepository repository = repository(target);

        mockMvc(repository).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.profileId").value(TARGET_PROFILE_ID.toString()))
                .andExpect(jsonPath("$.createdByProfileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.membershipStatus").value(nextStatus));

        assertEquals(target.withRoleAndStatus("MEMBER", nextStatus), repository.target);
        assertEquals(1, repository.updateCalls);
    }

    @Test
    void mapsTheLastOwnerInvariantToConflictWithoutPersisting() throws Exception {
        CapturingRepository repository = repository(member("OWNER", "ACTIVE"));
        repository.members = List.of(repository.target);

        mockMvc(repository).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"withdraw\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Membership owner required"))
                .andExpect(jsonPath("$.code").value("membership_owner_required"));

        assertEquals(0, repository.updateCalls);
        assertEquals("ACTIVE", repository.target.membershipStatus());
    }

    @Test
    void mapsAnUnsuccessfulConditionalUpdateToConflict() throws Exception {
        CapturingRepository repository = repository(member("MEMBER", "ACTIVE"));
        ProjectMember original = repository.target;
        repository.updatedRows = 0;

        mockMvc(repository).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"withdraw\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Membership conflict"))
                .andExpect(jsonPath("$.code").value("membership_conflict"));

        assertEquals(1, repository.updateCalls);
        assertEquals(original, repository.target);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "null", "{}", "{\"action\":null}", "{\"action\":\"   \"}",
            "{\"action\":\"promote\"}", "{\"action\":\"withdraw\"",
            "{\"action\":{}}", "{\"action\":\"changeRole\"}",
            "{\"action\":\"changeRole\",\"role\":null}",
            "{\"action\":\"changeRole\",\"role\":\"   \"}",
            "{\"action\":\"changeRole\",\"role\":\"SUPER_ADMIN\"}",
            "{\"action\":\"changeRole\",\"role\":{}}"
    })
    void rejectsMissingMalformedOrInvalidActionsAndRolesWithoutPersisting(String body) throws Exception {
        CapturingRepository repository = repository(member("MEMBER", "ACTIVE"));
        ProjectMember original = repository.target;

        mockMvc(repository).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());

        assertEquals(0, repository.updateCalls);
        assertEquals(original, repository.target);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "id", "projectId", "profileId", "createdByProfileId", "membershipStatus",
            "inviteeEmail", "inviteTokenDigest", "displayTitle", "isFeatured", "isPublic",
            "joinedAt", "createdAt", "updatedAt",
            "project_id", "profile_id", "created_by_profile_id", "membership_status",
            "invitee_email", "invite_token_digest", "display_title", "is_featured", "is_public",
            "joined_at", "created_at", "updated_at"
    })
    void rejectsClientControlledMembershipFieldsWithoutPersisting(String field) throws Exception {
        CapturingRepository repository = repository(member("MEMBER", "ACTIVE"));
        ProjectMember original = repository.target;

        mockMvc(repository).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"withdraw\",\"" + field + "\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        assertEquals(0, repository.updateCalls);
        assertEquals(original, repository.target);
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/members/" + MEMBER_ID;
    }

    private static MockMvc mockMvc(CapturingRepository repository) {
        ProjectMembershipsProperties properties = new ProjectMembershipsProperties(0, 20, 50, 100000);
        return MockMvcBuilders
                .standaloneSetup(new ProjectMembershipController(
                        new AdministerProjectMembership(repository, event -> {
                        }),
                        new ListProjectMemberships(repository),
                        properties))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
    }

    private static CapturingRepository repository(ProjectMember target) {
        CapturingRepository repository = new CapturingRepository();
        repository.target = target;
        repository.members = List.of(ownerMembership(), target);
        return repository;
    }

    private static ProjectMember ownerMembership() {
        return new ProjectMember(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                PROJECT_ID,
                PROFILE_ID,
                "OWNER",
                "ACTIVE",
                PROFILE_ID);
    }

    private static ProjectMember member(String role, String membershipStatus) {
        return new ProjectMember(
                MEMBER_ID,
                PROJECT_ID,
                TARGET_PROFILE_ID,
                role,
                membershipStatus,
                PROFILE_ID);
    }

    private static MembershipActor owner() {
        return new MembershipActor(PROJECT_ID, PROFILE_ID, "OWNER", "ACTIVE");
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

    private static final class CapturingRepository implements MembershipAdministrationRepository {

        private MembershipActor actor = owner();
        private ProjectMember target;
        private List<ProjectMember> members = List.of();
        private List<ManagedProjectMember> managedMembers = List.of();
        private int rosterReads;
        private int updateCalls;
        private int updatedRows = 1;

        @Override
        public void lockProject(UUID projectId) {
        }

        @Override
        public Optional<MembershipActor> findActor(UUID projectId, UUID profileId) {
            return Optional.ofNullable(actor);
        }

        @Override
        public Optional<ProjectMember> findMember(UUID projectId, UUID memberId) {
            return Optional.ofNullable(target);
        }

        @Override
        public List<ProjectMember> findMembers(UUID projectId) {
            return members;
        }

        @Override
        public List<ManagedProjectMember> findManagedMembers(UUID projectId, int offset, int limit) {
            rosterReads++;
            return managedMembers.stream().skip(offset).limit(limit).toList();
        }

        @Override
        public int updateMembership(
                UUID projectId,
                UUID memberId,
                String expectedRole,
                String expectedMembershipStatus,
                String nextRole,
                String nextMembershipStatus) {
            updateCalls++;
            assertEquals(target.role(), expectedRole);
            assertEquals(target.membershipStatus(), expectedMembershipStatus);
            if (updatedRows == 1) {
                target = target.withRoleAndStatus(nextRole, nextMembershipStatus);
            }
            return updatedRows;
        }
    }
}
