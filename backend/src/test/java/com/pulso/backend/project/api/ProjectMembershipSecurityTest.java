package com.pulso.backend.project.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.application.AdministerProjectMembership;
import com.pulso.backend.project.application.MembershipAdministrationRepository;
import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.ProjectMember;

@WebMvcTest(ProjectMembershipController.class)
@Import({SecurityConfig.class, ProjectMembershipSecurityTest.TestBeans.class})
class ProjectMembershipSecurityTest {

    private static final UUID OWNER_PROFILE_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACTOR_PROFILE_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TARGET_PROFILE_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID MEMBER_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MembershipFixtureRepository repository;

    @BeforeEach
    void resetMemberships() {
        repository.actors.clear();
        repository.actors.put(new ProjectProfile(PROJECT_ID, OWNER_PROFILE_ID),
                new MembershipActor(PROJECT_ID, OWNER_PROFILE_ID, "OWNER", "ACTIVE"));
        repository.target = member(TARGET_PROFILE_ID, "MEMBER", "ACTIVE");
        repository.updateCalls = 0;
    }

    @Test
    void requiresAnAccessTokenWithoutPersisting() throws Exception {
        mockMvc.perform(patch(path(PROJECT_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("withdraw")))
                .andExpect(status().isUnauthorized());

        assertEquals(0, repository.updateCalls);
    }

    @ParameterizedTest
    @MethodSource("allowedAdministrators")
    void allowsActiveOwnersAndAdminsFromPersistedMembership(String role, String action) throws Exception {
        repository.setActor(PROJECT_ID, role, "ACTIVE");
        repository.target = member(TARGET_PROFILE_ID, "MEMBER", targetStatus(action));

        mockMvc.perform(patch(path(PROJECT_ID))
                .with(jwt().jwt(authenticatedJwt(ACTOR_PROFILE_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(action)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("changeRole".equals(action) ? "ADMIN" : "MEMBER"))
                .andExpect(jsonPath("$.membershipStatus").value(
                        "withdraw".equals(action) ? "REMOVED" : "ACTIVE"));

        assertEquals(1, repository.updateCalls);
    }

    @ParameterizedTest
    @MethodSource("forbiddenActors")
    void deniesActorsWithoutActiveAdministrativeMembershipAcrossActions(
            String role, String membershipStatus, String action) throws Exception {
        if (!"EXTERNAL".equals(role)) {
            repository.setActor(PROJECT_ID, role, membershipStatus);
        }
        repository.target = member(TARGET_PROFILE_ID, "MEMBER", targetStatus(action));
        ProjectMember original = repository.target;

        mockMvc.perform(patch(path(PROJECT_ID))
                .with(jwt().jwt(authenticatedJwt(ACTOR_PROFILE_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(action)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("membership_permission"));

        assertEquals(0, repository.updateCalls);
        assertEquals(original, repository.target);
    }

    @Test
    void doesNotUseAnOwnerClaimToOverridePersistedMemberPermissions() throws Exception {
        repository.setActor(PROJECT_ID, "MEMBER", "ACTIVE");
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(ACTOR_PROFILE_ID.toString())
                .claim("role", "authenticated")
                .claim("projectRole", "OWNER")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(patch(path(PROJECT_ID))
                .with(jwt().jwt(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("changeRole")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("membership_permission"));

        assertEquals(0, repository.updateCalls);
        assertEquals("MEMBER", repository.target.role());
    }

    @Test
    void doesNotAuthorizeUsingMembershipFromAnotherProject() throws Exception {
        repository.setActor(OTHER_PROJECT_ID, "OWNER", "ACTIVE");

        mockMvc.perform(patch(path(PROJECT_ID))
                .with(jwt().jwt(authenticatedJwt(ACTOR_PROFILE_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("withdraw")))
                .andExpect(status().isForbidden());

        assertEquals(0, repository.updateCalls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "ADMIN"})
    void rejectsSelfElevationEvenForAnActiveAdmin(String role) throws Exception {
        repository.setActor(PROJECT_ID, role, "ACTIVE");
        repository.target = member(ACTOR_PROFILE_ID, role, "ACTIVE");

        mockMvc.perform(patch(path(PROJECT_ID))
                .with(jwt().jwt(authenticatedJwt(ACTOR_PROFILE_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"changeRole\",\"role\":\"OWNER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("membership_permission"));

        assertEquals(0, repository.updateCalls);
        assertEquals(role, repository.target.role());
    }

    @Test
    void rejectsAnAuthenticatedSubjectThatIsNotAProfileIdentifier() throws Exception {
        mockMvc.perform(patch(path(PROJECT_ID))
                .with(jwt().jwt(authenticatedJwt("not-a-profile-id")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("withdraw")))
                .andExpect(status().isUnauthorized());

        assertEquals(0, repository.updateCalls);
    }

    private static Stream<Arguments> allowedAdministrators() {
        return Stream.of("OWNER", "ADMIN").flatMap(role ->
                Stream.of("activate", "changeRole", "withdraw").map(action -> Arguments.of(role, action)));
    }

    private static Stream<Arguments> forbiddenActors() {
        return Stream.of(
                new String[] {"MEMBER", "ACTIVE"},
                new String[] {"EXTERNAL", "ACTIVE"},
                new String[] {"OWNER", "INVITED"},
                new String[] {"ADMIN", "INVITED"},
                new String[] {"OWNER", "REMOVED"},
                new String[] {"ADMIN", "REMOVED"},
                new String[] {"OWNER", "LEFT"},
                new String[] {"ADMIN", "LEFT"})
                .flatMap(actor -> Stream.of("activate", "changeRole", "withdraw")
                        .map(action -> Arguments.of(actor[0], actor[1], action)));
    }

    private static String targetStatus(String action) {
        return "activate".equals(action) ? "LEFT" : "ACTIVE";
    }

    private static String body(String action) {
        return "changeRole".equals(action)
                ? "{\"action\":\"changeRole\",\"role\":\"ADMIN\"}"
                : "{\"action\":\"" + action + "\"}";
    }

    private static String path(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/members/" + MEMBER_ID;
    }

    // jwt() supplies an authenticated principal; these tests exercise authorization,
    // not cryptographic JWT signature or issuer validation.
    private static Jwt authenticatedJwt(String subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
    }

    private static ProjectMember member(UUID profileId, String role, String membershipStatus) {
        return new ProjectMember(
                MEMBER_ID, PROJECT_ID, profileId, role, membershipStatus, OWNER_PROFILE_ID);
    }

    private record ProjectProfile(UUID projectId, UUID profileId) {
    }

    static final class MembershipFixtureRepository implements MembershipAdministrationRepository {

        private final Map<ProjectProfile, MembershipActor> actors = new HashMap<>();
        private ProjectMember target;
        private int updateCalls;

        void setActor(UUID projectId, String role, String membershipStatus) {
            actors.put(new ProjectProfile(projectId, ACTOR_PROFILE_ID),
                    new MembershipActor(projectId, ACTOR_PROFILE_ID, role, membershipStatus));
        }

        @Override
        public void lockProject(UUID projectId) {
        }

        @Override
        public Optional<MembershipActor> findActor(UUID projectId, UUID profileId) {
            return Optional.ofNullable(actors.get(new ProjectProfile(projectId, profileId)));
        }

        @Override
        public Optional<ProjectMember> findMember(UUID projectId, UUID memberId) {
            return target.projectId().equals(projectId) && target.id().equals(memberId)
                    ? Optional.of(target) : Optional.empty();
        }

        @Override
        public List<ProjectMember> findMembers(UUID projectId) {
            ProjectMember owner = new ProjectMember(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    PROJECT_ID, OWNER_PROFILE_ID, "OWNER", "ACTIVE", OWNER_PROFILE_ID);
            return PROJECT_ID.equals(projectId) ? List.of(owner, target) : List.of();
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
            assertEquals(target.projectId(), projectId);
            assertEquals(target.id(), memberId);
            assertEquals(target.role(), expectedRole);
            assertEquals(target.membershipStatus(), expectedMembershipStatus);
            target = target.withRoleAndStatus(nextRole, nextMembershipStatus);
            return 1;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        MembershipFixtureRepository membershipAdministrationRepository() {
            return new MembershipFixtureRepository();
        }

        @Bean
        AdministerProjectMembership administerProjectMembership(
                MembershipAdministrationRepository repository,
                ApplicationEventPublisher events) {
            return new AdministerProjectMembership(repository, events);
        }
    }
}
