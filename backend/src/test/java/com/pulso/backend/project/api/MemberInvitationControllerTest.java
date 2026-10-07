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
import com.pulso.backend.project.application.CreateMemberInvitation;
import com.pulso.backend.project.application.MemberInvitationRepository;
import com.pulso.backend.project.application.PersistedMemberInvitation;
import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;

class MemberInvitationControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INVITATION_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");

    @Test
    void createsAnInvitationWithoutExposingTokenMaterial() throws Exception {
        CapturingRepository repository = repository();

        mockMvc(repository).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteeEmail\":\" Ada@Example.COM \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(INVITATION_ID.toString()))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.inviteeEmail").value("ada@example.com"))
                .andExpect(jsonPath("$.profileId").doesNotExist())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.membershipStatus").value("INVITED"))
                .andExpect(jsonPath("$.createdByProfileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.inviteTokenDigest").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(repository.saved.inviteTokenDigest())
                .isEqualTo("69a24810112e8f3e257dfd85e240bcc8e8a60155787eaa0ec9526f584f0deb7d");
    }

    @Test
    void rejectsClientControlledInvitationFields() throws Exception {
        mockMvc(repository()).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteeEmail\":\"ada@example.com\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void mapsInvalidEmailToBadRequest() throws Exception {
        mockMvc(repository()).perform(post(path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteeEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid project"))
                .andExpect(jsonPath("$.detail").value("inviteeEmail is invalid."));
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/members";
    }

    private static MockMvc mockMvc(CapturingRepository repository) {
        return MockMvcBuilders
                .standaloneSetup(new MemberInvitationController(
                        new CreateMemberInvitation(
                                repository,
                                () -> "accept-me",
                                new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254),
                                (email, token) -> { })))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
    }

    private static CapturingRepository repository() {
        CapturingRepository repository = new CapturingRepository();
        repository.actor = new MemberInvitationActor(PROJECT_ID, PROFILE_ID, "OWNER", "ACTIVE");
        return repository;
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

    private static final class CapturingRepository implements MemberInvitationRepository {

        private MemberInvitationActor actor;
        private CreatedMemberInvitation saved;

        @Override
        public boolean projectExists(UUID projectId) {
            return true;
        }

        @Override
        public Optional<MemberInvitationActor> findActor(UUID projectId, UUID profileId) {
            return Optional.ofNullable(actor);
        }

        @Override
        public List<MemberInvitation> findInvitations(UUID projectId) {
            return List.of();
        }

        @Override
        public PersistedMemberInvitation createInvitation(CreatedMemberInvitation invitation) {
            saved = invitation;
            return new PersistedMemberInvitation(
                    INVITATION_ID,
                    invitation.invitation());
        }
    }
}
