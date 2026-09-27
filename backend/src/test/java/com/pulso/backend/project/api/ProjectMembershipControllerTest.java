package com.pulso.backend.project.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.project.application.AdministerProjectMembership;
import com.pulso.backend.project.application.MembershipAdministrationRepository;
import com.pulso.backend.project.domain.MembershipActor;
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
    }

    @Test
    void rejectsClientControlledMembershipFields() throws Exception {
        mockMvc(repository(member("MEMBER", "ACTIVE"))).perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"withdraw\",\"profileId\":\""
                        + PROFILE_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/members/" + MEMBER_ID;
    }

    private static MockMvc mockMvc(CapturingRepository repository) {
        return MockMvcBuilders
                .standaloneSetup(new ProjectMembershipController(
                        new AdministerProjectMembership(repository)))
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
        public int updateMembership(
                UUID projectId,
                UUID memberId,
                String expectedRole,
                String expectedMembershipStatus,
                String nextRole,
                String nextMembershipStatus) {
            target = target.withRoleAndStatus(nextRole, nextMembershipStatus);
            return 1;
        }
    }
}
