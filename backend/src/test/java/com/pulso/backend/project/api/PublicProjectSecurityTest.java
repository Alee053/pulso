package com.pulso.backend.project.api;

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
import com.pulso.backend.project.application.GetPublicProjectDetail;
import com.pulso.backend.project.application.PublicProjectRepository;
import com.pulso.backend.project.domain.PublicProjectDetail;

@WebMvcTest(PublicProjectController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class, PublicProjectSecurityTest.TestBeans.class })
class PublicProjectSecurityTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsAnonymousVisitorsToReadPublicProjects() throws Exception {
        mockMvc.perform(get("/api/v1/projects/eco-pack"))
                .andExpect(status().isOk());
    }

    @Test
    void keepsTheSameReadContractForAuthenticatedVisitors() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(get("/api/v1/projects/eco-pack").with(jwt().jwt(jwt)))
                .andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        PublicProjectRepository publicProjectRepository() {
            return reference -> Optional.of(new PublicProjectDetail(
                    PROJECT_ID,
                    "eco-pack",
                    "EcoPack Bolivia",
                    "Envases biodegradables",
                    null,
                    null,
                    new PublicProjectDetail.Sector(
                            UUID.fromString("11111111-1111-1111-1111-111111111111"),
                            "sostenibilidad",
                            "Sostenibilidad"),
                    "MVP",
                    "Cochabamba",
                    null,
                    List.of(),
                    List.of(),
                    List.of()));
        }

        @Bean
        GetPublicProjectDetail getPublicProjectDetail(PublicProjectRepository repository) {
            return new GetPublicProjectDetail(repository);
        }
    }
}
