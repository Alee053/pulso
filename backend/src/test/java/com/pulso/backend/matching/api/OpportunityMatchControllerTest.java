package com.pulso.backend.matching.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.matching.application.GetOpportunityMatches;
import com.pulso.backend.matching.application.OpportunityMatchRepository;
import com.pulso.backend.matching.domain.MatchOpportunity;
import com.pulso.backend.matching.domain.MatchProfile;

class OpportunityMatchControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPPORTUNITY_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String PATH = "/api/v1/projects/eco-pack/opportunity-matches";

    @Test
    void explainsTheMatchFactorByFactorForEachOpenOpportunity() throws Exception {
        MatchProfile onboarded = new MatchProfile(
                true, Set.of("WORK"), Set.of("sostenibilidad"), Set.of(), Set.of(), Set.of(), null, null);

        mockMvc(Optional.of(List.of(opportunity())), Optional.of(onboarded), false)
                .perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].opportunityId").value(OPPORTUNITY_ID.toString()))
                .andExpect(jsonPath("$.data[0].title").value("Designer"))
                .andExpect(jsonPath("$.data[0].score").value(75))
                .andExpect(jsonPath("$.data[0].onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data[0].missingFactors[0]").value("STAGE"))
                .andExpect(jsonPath("$.data[0].factors[0].key").value("SKILLS"))
                .andExpect(jsonPath("$.data[0].factors[0].status").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.data[0].factors[1].key").value("INTENTION"))
                .andExpect(jsonPath("$.data[0].factors[1].status").value("MATCHED"))
                .andExpect(jsonPath("$.data[0].factors[1].matched[0]").value("WORK"))
                .andExpect(jsonPath("$.data[0].factors[1].points").value(20));
    }

    @Test
    void answersAPersonWithoutOnboardingWithAValidResponseAndTheMissingFactors() throws Exception {
        MatchProfile empty = new MatchProfile(false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null, null);

        mockMvc(Optional.of(List.of(opportunity())), Optional.of(empty), false)
                .perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].score").value(0))
                .andExpect(jsonPath("$.data[0].onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data[0].missingFactors.length()").value(3));
    }

    @Test
    void returnsAnEmptyListWhenTheProjectHasNoOpenOpportunities() throws Exception {
        mockMvc(Optional.of(List.of()), Optional.of(onboardedProfile()), false)
                .perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void returnsTheSameNotFoundResponseForAnUnavailableProject() throws Exception {
        mockMvc(Optional.empty(), Optional.of(onboardedProfile()), false)
                .perform(get("/api/v1/projects/not-public/opportunity-matches"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("The project does not exist or is not publicly available."));
    }

    @Test
    void doesNotAcceptTheProfileFromTheClient() throws Exception {
        RecordingRepository repository = new RecordingRepository(
                Optional.of(List.of(opportunity())), Optional.of(onboardedProfile()));

        standalone(repository, false)
                .perform(get(PATH).queryParam("profileId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        assertThat(repository.profileId).isEqualTo(PROFILE_ID);
    }

    @Test
    void rejectsATokenWhoseSubjectIsNotAProfileId() throws Exception {
        mockMvc(Optional.of(List.of(opportunity())), Optional.of(onboardedProfile()), true)
                .perform(get(PATH))
                .andExpect(status().is4xxClientError());
    }

    private static MatchProfile onboardedProfile() {
        return new MatchProfile(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null, null);
    }

    private static MatchOpportunity opportunity() {
        return new MatchOpportunity(
                OPPORTUNITY_ID,
                "Designer",
                "TALENT",
                null,
                null,
                false,
                List.of(),
                new MatchOpportunity.Sector(
                        UUID.fromString("66666666-6666-6666-6666-666666666666"), "sostenibilidad", "Sostenibilidad"),
                "MVP",
                List.of());
    }

    private static MockMvc mockMvc(
            Optional<List<MatchOpportunity>> opportunities,
            Optional<MatchProfile> profile,
            boolean invalidSubject) {
        return standalone(new RecordingRepository(opportunities, profile), invalidSubject);
    }

    private static MockMvc standalone(RecordingRepository repository, boolean invalidSubject) {
        return MockMvcBuilders
                .standaloneSetup(new OpportunityMatchController(new GetOpportunityMatches(repository)))
                .setCustomArgumentResolvers(new JwtArgumentResolver(invalidSubject))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static final class RecordingRepository implements OpportunityMatchRepository {

        private final Optional<List<MatchOpportunity>> opportunities;
        private final Optional<MatchProfile> profile;
        private UUID profileId;

        private RecordingRepository(
                Optional<List<MatchOpportunity>> opportunities,
                Optional<MatchProfile> profile) {
            this.opportunities = opportunities;
            this.profile = profile;
        }

        @Override
        public Optional<List<MatchOpportunity>> findOpenOpportunities(String projectRef) {
            return opportunities;
        }

        @Override
        public Optional<MatchProfile> findProfile(UUID profileId) {
            this.profileId = profileId;
            return profile;
        }
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
                WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject(invalidSubject ? "not-a-uuid" : PROFILE_ID.toString())
                    .claim("role", "authenticated")
                    .audience(List.of("authenticated"))
                    .build();
        }
    }
}
