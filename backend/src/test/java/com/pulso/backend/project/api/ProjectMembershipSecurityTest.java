package com.pulso.backend.project.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

    private static final UUID PROFILE_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID MEMBER_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessToken() throws Exception {
        mockMvc.perform(patch(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"withdraw\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedUser() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(patch(path())
                .with(jwt().jwt(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"withdraw\"}"))
                .andExpect(status().isOk());
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/members/" + MEMBER_ID;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        MembershipAdministrationRepository membershipAdministrationRepository() {
            return new MembershipAdministrationRepository() {
                @Override
                public Optional<MembershipActor> findActor(UUID projectId, UUID profileId) {
                    return Optional.of(new MembershipActor(
                            PROJECT_ID,
                            PROFILE_ID,
                            "OWNER",
                            "ACTIVE"));
                }

                @Override
                public Optional<ProjectMember> findMember(UUID projectId, UUID memberId) {
                    return Optional.of(member());
                }

                @Override
                public List<ProjectMember> findMembers(UUID projectId) {
                    return List.of(owner(), member());
                }

                @Override
                public int updateMembership(
                        UUID projectId,
                        UUID memberId,
                        String expectedRole,
                        String expectedMembershipStatus,
                        String nextRole,
                        String nextMembershipStatus) {
                    return 1;
                }
            };
        }

        @Bean
        AdministerProjectMembership administerProjectMembership(
                MembershipAdministrationRepository repository) {
            return new AdministerProjectMembership(repository);
        }

        private static ProjectMember owner() {
            return new ProjectMember(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    PROJECT_ID,
                    PROFILE_ID,
                    "OWNER",
                    "ACTIVE",
                    PROFILE_ID);
        }

        private static ProjectMember member() {
            return new ProjectMember(
                    MEMBER_ID,
                    PROJECT_ID,
                    UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                    "MEMBER",
                    "ACTIVE",
                    PROFILE_ID);
        }
    }
}
