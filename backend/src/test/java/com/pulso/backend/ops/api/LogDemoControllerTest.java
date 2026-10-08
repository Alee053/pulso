package com.pulso.backend.ops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.discovery.api.ApiExceptionHandler;

@WebMvcTest(LogDemoController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class })
@ActiveProfiles("log-demo")
@ExtendWith(OutputCaptureExtension.class)
class LogDemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void simulatesADataStoreFailureAndLogsItWithoutAuthentication(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/dev/log-demo/data-store-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Data store unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail")
                        .value("The data store is temporarily unavailable."));

        assertThat(output.getAll())
                .contains("\"level\":\"ERROR\"")
                .contains("\"operation\":\"dataStoreUnavailable\"")
                .contains("\"message\":\"password=[REDACTED]\"")
                .doesNotContain("demo-only-value");
    }
}
