package com.pulso.backend.ops.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;

@WebMvcTest(LogDemoController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class LogDemoControllerProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deniesTheDemoEndpointWhenTheLogDemoProfileIsInactive() throws Exception {
        mockMvc.perform(post("/api/dev/log-demo/data-store-failure"))
                .andExpect(status().isUnauthorized());
    }
}
