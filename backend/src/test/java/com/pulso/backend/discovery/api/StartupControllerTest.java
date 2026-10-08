package com.pulso.backend.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.pulso.backend.config.DiscoveryProperties;
import com.pulso.backend.discovery.application.ListPublishedStartups;
import com.pulso.backend.discovery.application.StartupCatalog;
import com.pulso.backend.discovery.domain.StartupSummary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class StartupControllerTest {

    private static final DiscoveryProperties PROPERTIES =
            new DiscoveryProperties(0, 20, 50, 100_000);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockMvc mockMvc = mockMvc(catalog());

    @Test
    void returnsAStableStartupListContract() throws Exception {
        mockMvc.perform(get("/api/v1/startups").param("offset", "0").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("eco-pack"))
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.hasMore").value(true));
    }

    @Test
    void rejectsAnUnboundedPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/startups").param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsANonNumericPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/startups").param("limit", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsANegativeOffset() throws Exception {
        mockMvc.perform(get("/api/v1/startups").param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void reportsUnavailableDataStoreAndWritesAnErrorLog(CapturedOutput output) throws Exception {
        MockMvc unavailableMockMvc = mockMvc((offset, limit) -> {
            throw new DataAccessResourceFailureException("password=secret host=database");
        });

        unavailableMockMvc.perform(get("/api/v1/startups")
                        .param("offset", "0")
                        .param("limit", "20"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Data store unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail")
                        .value("The data store is temporarily unavailable."));

        JsonNode event = event(output.getAll(), "dataStoreUnavailable");

        assertThat(event.path("level").stringValue()).isEqualTo("ERROR");
        assertThat(event.path("module").stringValue()).isEqualTo("api");
        assertThat(event.path("operation").stringValue()).isEqualTo("dataStoreUnavailable");
        assertThat(event.path("metadata").path("httpStatus").asInt()).isEqualTo(503);
        assertThat(event.path("exception").path("type").stringValue())
                .isEqualTo("DataAccessResourceFailureException");
        assertThat(event.path("exception").path("message").stringValue())
                .isEqualTo("password=[REDACTED]");
        assertThat(event.toString()).doesNotContain("password=secret");
    }

    private static MockMvc mockMvc(StartupCatalog catalog) {
        return MockMvcBuilders
                .standaloneSetup(new StartupController(
                        new ListPublishedStartups(catalog),
                        PROPERTIES))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static StartupCatalog catalog() {
        return (offset, limit) -> List.of(
                new StartupSummary(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "eco-pack",
                        "EcoPack Bolivia",
                        "Envases biodegradables",
                        "Sostenibilidad",
                        "Cochabamba",
                        "MVP"),
                new StartupSummary(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "ande-solar",
                        "AndeSolar",
                        "Sistemas solares modulares",
                        "Energía limpia",
                        "La Paz",
                        "MVP"));
    }

    private static JsonNode event(String output, String operation) throws Exception {
        String line = output.lines()
                .filter(candidate -> candidate.contains("\"operation\":\"" + operation + "\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No log event found for operation " + operation));
        String cleanLine = line.replaceAll("\\u001B\\[[;\\d]*m", "");
        int jsonStart = cleanLine.indexOf('{');
        if (jsonStart < 0) {
            throw new AssertionError("Log event is not JSON: " + cleanLine);
        }
        return JSON.readTree(cleanLine.substring(jsonStart));
    }
}
