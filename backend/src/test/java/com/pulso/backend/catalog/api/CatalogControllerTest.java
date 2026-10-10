package com.pulso.backend.catalog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.pulso.backend.catalog.application.ActiveCatalogRepository;
import com.pulso.backend.catalog.application.ListActiveCatalogs;
import com.pulso.backend.catalog.domain.SectorCatalogItem;
import com.pulso.backend.catalog.domain.SkillCatalogItem;
import com.pulso.backend.catalog.domain.TagCatalogItem;
import com.pulso.backend.config.CatalogProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;

class CatalogControllerTest {

    @Test
    void returnsTheVersionedCatalogContract() throws Exception {
        MockMvc mockMvc = mockMvc(new FixedRepository(
                List.of(new SectorCatalogItem(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "tecnologia",
                        "Tecnología",
                        "Productos y servicios tecnológicos.")),
                List.of(new TagCatalogItem(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "inteligencia-artificial",
                        "Inteligencia artificial",
                        "TOPIC")),
                List.of(new SkillCatalogItem(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "java",
                        "Java",
                        "DESARROLLO"))));

        mockMvc.perform(get("/api/v1/catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectors[0].id")
                        .value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.data.sectors[0].slug").value("tecnologia"))
                .andExpect(jsonPath("$.data.sectors[0].name").value("Tecnología"))
                .andExpect(jsonPath("$.data.sectors[0].description")
                        .value("Productos y servicios tecnológicos."))
                .andExpect(jsonPath("$.data.tags[0].slug").value("inteligencia-artificial"))
                .andExpect(jsonPath("$.data.tags[0].type").value("TOPIC"))
                .andExpect(jsonPath("$.data.skills[0].slug").value("java"))
                .andExpect(jsonPath("$.data.skills[0].category").value("DESARROLLO"))
                .andExpect(jsonPath("$.data.intentions").value(org.hamcrest.Matchers.contains(
                        "DISCOVER", "BUY", "COLLABORATE", "WORK", "INVEST", "FIND_PARTNERS",
                        "FIND_CUSTOMERS", "FIND_PROVIDERS", "EXPLORE_ECOSYSTEM")))
                .andExpect(jsonPath("$.data.stages").value(org.hamcrest.Matchers.contains(
                        "IDEA", "PROTOTYPE", "MVP", "EARLY_STAGE", "GROWING", "ESTABLISHED")));
    }

    @Test
    void returnsEmptyArraysWhenThereAreNoActiveEntries() throws Exception {
        mockMvc(new FixedRepository(List.of(), List.of(), List.of()))
                .perform(get("/api/v1/catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectors").isEmpty())
                .andExpect(jsonPath("$.data.tags").isEmpty())
                .andExpect(jsonPath("$.data.skills").isEmpty());
    }

    @Test
    void returnsServiceUnavailableWithoutLeakingDatabaseDetails() throws Exception {
        ActiveCatalogRepository unavailableRepository = new FixedRepository(
                List.of(), List.of(), List.of()) {
            @Override
            public List<SectorCatalogItem> findActiveSectors(int limit) {
                throw new DataAccessResourceFailureException("password=secret host=internal-db");
            }
        };

        mockMvc(unavailableRepository)
                .perform(get("/api/v1/catalogs"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Data store unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The data store is temporarily unavailable."));
    }

    private static MockMvc mockMvc(ActiveCatalogRepository repository) {
        return MockMvcBuilders
                .standaloneSetup(new CatalogController(new ListActiveCatalogs(
                        repository,
                        new CatalogProperties(500))))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static class FixedRepository implements ActiveCatalogRepository {

        private final List<SectorCatalogItem> sectors;
        private final List<TagCatalogItem> tags;
        private final List<SkillCatalogItem> skills;

        private FixedRepository(
                List<SectorCatalogItem> sectors,
                List<TagCatalogItem> tags,
                List<SkillCatalogItem> skills) {
            this.sectors = sectors;
            this.tags = tags;
            this.skills = skills;
        }

        @Override
        public List<SectorCatalogItem> findActiveSectors(int limit) {
            return sectors;
        }

        @Override
        public List<TagCatalogItem> findActiveTags(int limit) {
            return tags;
        }

        @Override
        public List<SkillCatalogItem> findActiveSkills(int limit) {
            return skills;
        }
    }
}
