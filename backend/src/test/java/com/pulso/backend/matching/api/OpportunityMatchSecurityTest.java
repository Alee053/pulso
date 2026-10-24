package com.pulso.backend.matching.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.matching.application.GetOpportunityMatches;
import com.pulso.backend.matching.application.OpportunityMatchRepository;
import com.pulso.backend.matching.domain.MatchOpportunity;
import com.pulso.backend.matching.domain.MatchProfile;

@WebMvcTest(OpportunityMatchController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class, OpportunityMatchSecurityTest.TestBeans.class })
class OpportunityMatchSecurityTest {

    private static final String PATH = "/api/v1/projects/eco-pack/opportunity-matches";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAuthenticationBecauseTheMatchIsPersonal() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedPersonToReadTheirOwnMatch() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(get(PATH).with(jwt().jwt(jwt))).andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        GetOpportunityMatches getOpportunityMatches() {
            return new GetOpportunityMatches(new OpportunityMatchRepository() {
                @Override
                public Optional<List<MatchOpportunity>> findOpenOpportunities(String projectRef) {
                    return Optional.of(List.of());
                }

                @Override
                public Optional<MatchProfile> findProfile(UUID profileId) {
                    return Optional.of(new MatchProfile(
                            false, null, null, null, null, null, null, null));
                }
            });
        }
    }
}
