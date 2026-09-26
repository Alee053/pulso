package com.pulso.backend.discovery.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.pulso.backend.config.DiscoveryProperties;
import com.pulso.backend.discovery.application.ListPublishedStartups;
import com.pulso.backend.discovery.application.StartupCatalog;
import com.pulso.backend.discovery.domain.StartupSummary;

class StartupControllerTest {

    private static final DiscoveryProperties PROPERTIES =
            new DiscoveryProperties(0, 20, 50, 100_000);

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
    void appliesConfiguredDefaultsWhenPaginationParametersAreOmitted() throws Exception {
        mockMvc(catalog(), new DiscoveryProperties(3, 1, 1, 10))
                .perform(get("/api/v1/startups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.offset").value(3))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.hasMore").value(true));
    }

    @Test
    void appliesConfiguredMaximumPageSize() throws Exception {
        mockMvc(catalog(), new DiscoveryProperties(0, 1, 1, 100_000))
                .perform(get("/api/v1/startups").param("limit", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
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
    void reportsUnavailableDataStoreAsServiceUnavailable() throws Exception {
        MockMvc unavailableMockMvc = mockMvc((offset, limit) -> {
            throw new DataAccessResourceFailureException("database unavailable");
        });

        unavailableMockMvc.perform(get("/api/v1/startups").param("offset", "0").param("limit", "20"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Data store unavailable"))
                .andExpect(jsonPath("$.status").value(503));
    }

    private static MockMvc mockMvc(StartupCatalog catalog) {
        return mockMvc(catalog, PROPERTIES);
    }

    private static MockMvc mockMvc(
            StartupCatalog catalog,
            DiscoveryProperties properties) {
        return MockMvcBuilders
                .standaloneSetup(new StartupController(
                        new ListPublishedStartups(catalog),
                        properties))
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
}
