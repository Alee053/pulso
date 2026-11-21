package com.pulso.backend.profile.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
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
import org.springframework.web.bind.support.WebDataBinderFactory;

import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.profile.application.ManageProfileBlocks;
import com.pulso.backend.profile.application.ProfileBlockRepository;
import com.pulso.backend.profile.application.ProfileRepository;
import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfileBlock;
import com.pulso.backend.profile.domain.ProfilePatch;

class ProfileBlockControllerTest {
    private static final UUID ACTOR_ID = id("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = id("22222222-2222-2222-2222-222222222222");
    private static final String PATH = "/api/v1/me/blocks/" + TARGET_ID;

    @Test
    void exposesPrivateStatusAndIdempotentBlockActions() throws Exception {
        InMemoryBlocks blocks = new InMemoryBlocks();
        MockMvc mvc = mockMvc(blocks, false);

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetProfileId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetProfileId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.createdAt").exists());
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(delete(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rejectsSelfBlockAndUnavailableTargetWithoutRevealingMoreData() throws Exception {
        MockMvc mvc = mockMvc(new InMemoryBlocks(), false);
        mvc.perform(put("/api/v1/me/blocks/" + ACTOR_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A profile cannot block itself."));

        mvc.perform(get("/api/v1/me/blocks/33333333-3333-3333-3333-333333333333"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnInvalidJwtSubject() throws Exception {
        mockMvc(new InMemoryBlocks(), true).perform(get(PATH))
                .andExpect(status().isUnauthorized());
    }

    private static MockMvc mockMvc(InMemoryBlocks blocks, boolean invalidSubject) {
        ProfileRepository profiles = new ProfileRepository() {
            @Override
            public Optional<Profile> findById(UUID profileId) {
                return profileId.equals(TARGET_ID) ? Optional.of(profile()) : Optional.empty();
            }

            @Override
            public Optional<Profile> update(UUID profileId, ProfilePatch patch) { return Optional.empty(); }
        };
        return MockMvcBuilders.standaloneSetup(new ProfileBlockController(
                        new ManageProfileBlocks(profiles, blocks)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver(invalidSubject))
                .build();
    }

    private static Profile profile() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-21T12:00:00Z");
        return new Profile(TARGET_ID, "PERSON", "Ada", null, null, null, null,
                "PUBLIC", "UNVERIFIED", "ACTIVE", "USER", null, now, now);
    }

    private static UUID id(String value) { return UUID.fromString(value); }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {
        private final boolean invalidSubject;

        private JwtArgumentResolver(boolean invalidSubject) { this.invalidSubject = invalidSubject; }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("test-token").header("alg", "HS256")
                    .subject(invalidSubject ? "invalid-subject" : ACTOR_ID.toString()).build();
        }
    }

    private static final class InMemoryBlocks implements ProfileBlockRepository {
        private final Map<String, ProfileBlock> values = new HashMap<>();

        @Override
        public Optional<ProfileBlock> find(UUID actor, UUID target) {
            return Optional.ofNullable(values.get(key(actor, target)));
        }

        @Override
        public ProfileBlock createIfAbsent(UUID actor, UUID target) {
            return values.computeIfAbsent(key(actor, target), ignored -> ProfileBlock.blocked(
                    target, OffsetDateTime.parse("2026-11-21T12:00:00Z")));
        }

        @Override
        public void delete(UUID actor, UUID target) { values.remove(key(actor, target)); }

        private static String key(UUID actor, UUID target) { return actor + ":" + target; }
    }
}
