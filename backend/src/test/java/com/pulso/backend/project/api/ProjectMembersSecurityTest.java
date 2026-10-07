package com.pulso.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.application.ListProjectMembers;
import com.pulso.backend.project.application.ProjectMembersReader;
import com.pulso.backend.project.application.ProjectMembersSnapshot;
import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.ProjectMemberSummary;

@WebMvcTest(ProjectMembersController.class)
@Import({SecurityConfig.class, ProjectMembersSecurityTest.TestBeans.class})
class ProjectMembersSecurityTest {

    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FixtureReader reader;

    @BeforeEach
    void reset() {
        reader.role = "OWNER";
        reader.members = List.of(member());
        reader.available = true;
        reader.fail = false;
        reader.calls = 0;
    }

    @Test
    void requiresAuthenticationBeforeReadingMemberships() throws Exception {
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
        assertThat(reader.calls).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "ADMIN", "MEMBER"})
    void returnsOnlyTheContractWithPersistedRoleIgnoringProjectClaims(String role) throws Exception {
        reader.role = role;
        mockMvc.perform(get(path()).with(jwt().jwt(token(PROFILE_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actor.role").value(role))
                .andExpect(jsonPath("$.actor.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.actor.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$.data[0].projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.data[0].profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.data[0].displayName").value("Integrante"))
                .andExpect(jsonPath("$.data[0].displayTitle").value("Equipo"))
                .andExpect(jsonPath("$.data[0].length()").value(7))
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
        assertThat(reader.calls).isEqualTo(1);
        assertThat(reader.limit).isEqualTo(21);
    }

    @Test
    void serializesMaskedNamesAndUnboundInvitationIdentityAsNull() throws Exception {
        reader.members = List.of(new ProjectMemberSummary(
                MEMBER_ID, PROJECT_ID, null, "MEMBER", "INVITED", null, null));
        mockMvc.perform(get(path()).with(jwt().jwt(token(PROFILE_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].profileId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].displayName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].displayTitle").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].membershipStatus").value("INVITED"))
                .andExpect(jsonPath("$.data[0].inviteeEmail").doesNotExist())
                .andExpect(jsonPath("$.data[0].inviteTokenDigest").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdByProfileId").doesNotExist());
    }

    @Test
    void fetchesOneExtraAndTrimsThePageWithoutExposingExtraRows() throws Exception {
        reader.members = List.of(member(), member(), member());
        mockMvc.perform(get(path()).queryParam("offset", "10").queryParam("limit", "2")
                .with(jwt().jwt(token(PROFILE_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.offset").value(10))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.hasMore").value(true));
        assertThat(reader.offset).isEqualTo(10);
        assertThat(reader.limit).isEqualTo(3);
    }

    @Test
    void returnsAnAuthorizedEmptyPageIncludingTheActor() throws Exception {
        reader.members = List.of();
        mockMvc.perform(get(path()).with(jwt().jwt(token(PROFILE_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.actor.role").value("OWNER"))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit=0", "limit=-1", "limit=51", "offset=-1", "offset=100001", "offset=invalid"})
    void rejectsInvalidWindowsWithoutReading(String parameter) throws Exception {
        String[] query = parameter.split("=");
        mockMvc.perform(get(path()).queryParam(query[0], query[1])
                .with(jwt().jwt(token(PROFILE_ID.toString()))))
                .andExpect(status().isBadRequest());
        assertThat(reader.calls).isZero();
    }

    @Test
    void rejectsInvalidSubjectBeforeReading() throws Exception {
        mockMvc.perform(get(path()).with(jwt().jwt(token("invalid-profile"))))
                .andExpect(status().isUnauthorized());
        assertThat(reader.calls).isZero();
    }

    @Test
    void mapsMissingAuthorizationWithoutExposingTeamInformation() throws Exception {
        reader.available = false;
        mockMvc.perform(get(path()).with(jwt().jwt(token(PROFILE_ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("membership_permission"))
                .andExpect(jsonPath("$.detail").value("active project membership is required."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void mapsDatabaseFailureToSanitizedUnavailableProblem() throws Exception {
        reader.fail = true;
        mockMvc.perform(get(path()).with(jwt().jwt(token(PROFILE_ID.toString()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/members";
    }

    // jwt() exercises HTTP authentication/authorization, not cryptographic token validation.
    private static Jwt token(String subject) {
        return Jwt.withTokenValue("test-token").header("alg", "HS256")
                .subject(subject).claim("role", "authenticated").claim("projectRole", "OWNER")
                .audience(List.of("authenticated")).build();
    }

    private static ProjectMemberSummary member() {
        return new ProjectMemberSummary(MEMBER_ID, PROJECT_ID, PROFILE_ID,
                "MEMBER", "ACTIVE", "Integrante", "Equipo");
    }

    static final class FixtureReader implements ProjectMembersReader {
        private String role;
        private List<ProjectMemberSummary> members;
        private boolean available;
        private boolean fail;
        private int calls;
        private int offset;
        private int limit;

        @Override
        public Optional<ProjectMembersSnapshot> findAuthorizedPage(UUID projectId, UUID profileId, int offset, int limit) {
            assertThat(projectId).isEqualTo(PROJECT_ID);
            assertThat(profileId).isEqualTo(PROFILE_ID);
            calls++;
            this.offset = offset;
            this.limit = limit;
            if (fail) {
                throw new DataAccessResourceFailureException("Data store unavailable");
            }
            return available ? Optional.of(new ProjectMembersSnapshot(
                    new MembershipActor(projectId, profileId, role, "ACTIVE"), members)) : Optional.empty();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        FixtureReader projectMembersReader() {
            return new FixtureReader();
        }

        @Bean
        ListProjectMembers listProjectMembers(ProjectMembersReader reader) {
            return new ListProjectMembers(reader);
        }
    }
}
