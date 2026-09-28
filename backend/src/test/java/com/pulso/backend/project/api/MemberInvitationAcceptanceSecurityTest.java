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

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.application.AcceptMemberInvitation;
import com.pulso.backend.project.application.MemberInvitationAcceptanceRepository;
import com.pulso.backend.project.application.PersistedMemberInvitation;
import com.pulso.backend.project.domain.InviteTokenDigest;
import com.pulso.backend.project.domain.MemberInvitation;

@WebMvcTest(MemberInvitationAcceptanceController.class)
@Import({SecurityConfig.class, MemberInvitationAcceptanceSecurityTest.TestBeans.class})
class MemberInvitationAcceptanceSecurityTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CREATOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String TOKEN = "accept-me";
    private static final String DIGEST = InviteTokenDigest.fromPlaintext(TOKEN);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessToken() throws Exception {
        mockMvc.perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedUserAndUsesTheProtectedEndpoint() throws Exception {
        Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("email", "ada@example.com")
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(post(path())
                .with(jwt().jwt(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk());
    }

    private static String path() {
        return "/api/v1/project-members/accept";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        MemberInvitationAcceptanceRepository memberInvitationAcceptanceRepository() {
            return new MemberInvitationAcceptanceRepository() {
                private PersistedMemberInvitation invitation = new PersistedMemberInvitation(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        new MemberInvitation(
                                PROJECT_ID,
                                "ada@example.com",
                                null,
                                "MEMBER",
                                "INVITED",
                                CREATOR_ID,
                                new MemberInvitation.Limits(254)));

                @Override
                public Optional<PersistedMemberInvitation> findByInviteTokenDigest(
                        String inviteTokenDigest) {
                    return DIGEST.equals(inviteTokenDigest)
                            ? Optional.of(invitation)
                            : Optional.empty();
                }

                @Override
                public boolean hasMembership(UUID projectId, UUID profileId) {
                    return false;
                }

                @Override
                public Optional<PersistedMemberInvitation> acceptInvitation(
                        UUID invitationId,
                        String inviteTokenDigest,
                        UUID profileId) {
                    invitation = new PersistedMemberInvitation(
                            invitation.id(),
                            invitation.invitation().accept(profileId));
                    return Optional.of(invitation);
                }
            };
        }

        @Bean
        AcceptMemberInvitation acceptMemberInvitation(
                MemberInvitationAcceptanceRepository repository) {
            return new AcceptMemberInvitation(repository);
        }
    }
}
