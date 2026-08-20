package com.pulso.backend.discovery.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesOnlyTheAuthenticatedIdentityClaims() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("email", "developer@example.test")
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.email").value("developer@example.test"))
                .andExpect(jsonPath("$.role").value("authenticated"));
    }
}
