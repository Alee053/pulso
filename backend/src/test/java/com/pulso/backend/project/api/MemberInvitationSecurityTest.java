package com.pulso.backend.project.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.application.CreateMemberInvitation;
import com.pulso.backend.project.application.MemberInvitationRepository;
import com.pulso.backend.project.application.MemberInvitationTokenGenerator;
import com.pulso.backend.project.application.PersistedMemberInvitation;
import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;

@WebMvcTest(MemberInvitationController.class)
@Import({SecurityConfig.class, MemberInvitationSecurityTest.TestBeans.class})
class MemberInvitationSecurityTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessToken() throws Exception {
        mockMvc.perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteeEmail\":\"ada@example.com\"}"))
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

        mockMvc.perform(post(path())
                .with(jwt().jwt(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteeEmail\":\"ada@example.com\"}"))
                .andExpect(status().isCreated());
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/members";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        MemberInvitationRepository memberInvitationRepository() {
            return new MemberInvitationRepository() {
                @Override
                public boolean projectExists(UUID projectId) {
                    return true;
                }

                @Override
                public Optional<MemberInvitationActor> findActor(UUID projectId, UUID profileId) {
                    return Optional.of(new MemberInvitationActor(
                            PROJECT_ID,
                            PROFILE_ID,
                            "OWNER",
                            "ACTIVE"));
                }

                @Override
                public List<MemberInvitation> findInvitations(UUID projectId) {
                    return List.of();
                }

                @Override
                public PersistedMemberInvitation createInvitation(
                        CreatedMemberInvitation invitation) {
                    return new PersistedMemberInvitation(
                            UUID.fromString("55555555-5555-5555-5555-555555555555"),
                            invitation.invitation());
                }
            };
        }

        @Bean
        MemberInvitationTokenGenerator memberInvitationTokenGenerator() {
            return () -> "accept-me";
        }

        @Bean
        CreateMemberInvitation createMemberInvitation(
                MemberInvitationRepository repository,
                MemberInvitationTokenGenerator tokenGenerator,
                ProjectProperties properties) {
            return new CreateMemberInvitation(repository, tokenGenerator, properties, (email, token) -> { });
        }
    }
}
