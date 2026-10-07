package com.pulso.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.project.application.AcceptMemberInvitation;
import com.pulso.backend.project.application.MemberInvitationAcceptanceRepository;
import com.pulso.backend.project.application.PersistedMemberInvitation;
import com.pulso.backend.project.domain.InviteTokenDigest;
import com.pulso.backend.project.domain.MemberInvitation;

class MemberInvitationAcceptanceControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID CREATOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROFILE_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID INVITATION_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final String TOKEN = "accept-me";
    private static final String DIGEST = InviteTokenDigest.fromPlaintext(TOKEN);
    private static final ProjectProperties PROPERTIES =
            new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254);

    @Test
    void acceptsAnInvitationUsingTheJwtIdentityAndDoesNotExposeTokenMaterial() throws Exception {
        CapturingRepository repository = repository();

        mockMvc(repository, authenticatedJwt(" Ada@Example.COM ")).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(INVITATION_ID.toString()))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.inviteeEmail").value("ada@example.com"))
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.inviteTokenDigest").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.plaintextToken").doesNotExist());

        assertThat(repository.lookupDigest).isEqualTo(DIGEST);
        assertThat(repository.acceptedDigest).isEqualTo(DIGEST);
        assertThat(repository.acceptedProfileId).isEqualTo(PROFILE_ID);
    }

    @Test
    void rejectsAnEmailMismatchWithoutChangingTheInvitation() throws Exception {
        CapturingRepository repository = repository();

        mockMvc(repository, authenticatedJwt("other@example.com")).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Member invitation"))
                .andExpect(jsonPath("$.detail").value("invitation not found or already used."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(TOKEN))));

        assertThat(repository.acceptCalls).isZero();
        assertThat(repository.pending.invitation().profileId()).isNull();
        assertThat(repository.pending.invitation().membershipStatus()).isEqualTo("INVITED");
    }

    @Test
    void rejectsInvalidTokensWithoutEchoingTheToken() throws Exception {
        CapturingRepository repository = repository();
        String invalidToken = "invalid-token";

        mockMvc(repository, authenticatedJwt("ada@example.com")).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + invalidToken + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(invalidToken))));

        assertThat(repository.acceptCalls).isZero();
        assertThat(repository.pending.invitation().profileId()).isNull();
    }

    @Test
    void rejectsClientControlledIdentityFields() throws Exception {
        mockMvc(repository(), authenticatedJwt("ada@example.com")).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN
                        + "\",\"profileId\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsAnEmptyBodyAsAnInvalidRequest() throws Exception {
        mockMvc(repository(), authenticatedJwt("ada@example.com"))
                .perform(post(path()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid project"))
                .andExpect(jsonPath("$.detail").value("request body is required."));
    }

    private static String path() {
        return "/api/v1/project-members/accept";
    }

    private static MockMvc mockMvc(CapturingRepository repository, Jwt jwt) {
        return MockMvcBuilders
                .standaloneSetup(new MemberInvitationAcceptanceController(
                        new AcceptMemberInvitation(repository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver(jwt))
                .build();
    }

    private static Jwt authenticatedJwt(String email) {
        return Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("email", email)
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
    }

    private static CapturingRepository repository() {
        return new CapturingRepository();
    }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {

        private final Jwt jwt;

        private JwtArgumentResolver(Jwt jwt) {
            this.jwt = jwt;
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
            return jwt;
        }
    }

    private static final class CapturingRepository
            implements MemberInvitationAcceptanceRepository {

        private PersistedMemberInvitation pending = new PersistedMemberInvitation(
                INVITATION_ID,
                new MemberInvitation(
                        PROJECT_ID,
                        "ada@example.com",
                        null,
                        "MEMBER",
                        "INVITED",
                        CREATOR_ID,
                        new MemberInvitation.Limits(PROPERTIES.inviteeEmailMaxLength())));
        private String lookupDigest;
        private String acceptedDigest;
        private UUID acceptedProfileId;
        private int acceptCalls;

        @Override
        public Optional<PersistedMemberInvitation> findByInviteTokenDigest(String digest) {
            lookupDigest = digest;
            return DIGEST.equals(digest) ? Optional.of(pending) : Optional.empty();
        }

        @Override
        public boolean hasMembership(UUID projectId, UUID profileId) {
            return false;
        }

        @Override
        public Optional<PersistedMemberInvitation> acceptInvitation(
                UUID invitationId,
                String digest,
                UUID profileId) {
            acceptCalls++;
            acceptedDigest = digest;
            acceptedProfileId = profileId;
            pending = new PersistedMemberInvitation(
                    pending.id(),
                    pending.invitation().accept(profileId));
            return Optional.of(pending);
        }
    }
}
