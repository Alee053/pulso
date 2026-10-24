package com.pulso.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.application.ProjectSearchRepository;
import com.pulso.backend.project.application.SearchProjects;
import com.pulso.backend.project.domain.ProjectSearchFilters;

class ProjectSearchControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");

    @Test
    void returnsTheMatchingProjectsWithPaginationMetadata() throws Exception {
        RecordingRepository repository = new RecordingRepository();

        mockMvc(repository).perform(get("/api/v1/projects/search")
                .queryParam("offset", "20")
                .queryParam("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.data[0].slug").value("eco-pack"))
                .andExpect(jsonPath("$.data[0].sector").value("Sostenibilidad"))
                .andExpect(jsonPath("$.meta.offset").value(20))
                .andExpect(jsonPath("$.meta.limit").value(10))
                .andExpect(jsonPath("$.meta.totalCount").value(31));

        assertThat(repository.offset).isEqualTo(20);
        assertThat(repository.limit).isEqualTo(10);
    }

    @Test
    void normalizesFiltersBeforeSearching() throws Exception {
        RecordingRepository repository = new RecordingRepository();

        mockMvc(repository).perform(get("/api/v1/projects/search")
                .queryParam("q", "  envases ")
                .queryParam("sector", "sostenibilidad")
                .queryParam("stage", "mvp")
                .queryParam("location", "   ")
                .queryParam("tags", "impacto", "impacto", " ")
                .queryParam("hasOpenOpportunities", "true"))
                .andExpect(status().isOk());

        assertThat(repository.filters).isEqualTo(new ProjectSearchFilters(
                "envases", "sostenibilidad", "MVP", null, List.of("impacto"), true));
        assertThat(repository.offset).isZero();
        assertThat(repository.limit).isEqualTo(20);
    }

    @Test
    void searchesWithoutFiltersWhenNoneAreSent() throws Exception {
        RecordingRepository repository = new RecordingRepository();

        mockMvc(repository).perform(get("/api/v1/projects/search"))
                .andExpect(status().isOk());

        assertThat(repository.filters).isEqualTo(
                new ProjectSearchFilters(null, null, null, null, List.of(), null));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        for (String[] page : new String[][] { { "-1", "20" }, { "0", "0" }, { "0", "51" } }) {
            RecordingRepository repository = new RecordingRepository();

            mockMvc(repository).perform(get("/api/v1/projects/search")
                    .queryParam("offset", page[0])
                    .queryParam("limit", page[1]))
                    .andExpect(status().isBadRequest());

            assertThat(repository.filters).isNull();
        }
    }

    @Test
    void rejectsAnUnknownStage() throws Exception {
        RecordingRepository repository = new RecordingRepository();

        mockMvc(repository).perform(get("/api/v1/projects/search").queryParam("stage", "UNICORN"))
                .andExpect(status().isBadRequest());

        assertThat(repository.filters).isNull();
    }

    @Test
    void rejectsOversizedTextFilters() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        String tooLong = "a".repeat(ProjectSearchController.MAX_TEXT_LENGTH + 1);

        for (String name : List.of("q", "sector", "location", "tags")) {
            mockMvc(repository).perform(get("/api/v1/projects/search").queryParam(name, tooLong))
                    .andExpect(status().isBadRequest());
        }

        assertThat(repository.filters).isNull();
    }

    @Test
    void rejectsTooManyTags() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        List<String> tags = new ArrayList<>();
        for (int index = 0; index <= ProjectSearchController.MAX_TAGS; index++) {
            tags.add("tag-" + index);
        }

        mockMvc(repository).perform(get("/api/v1/projects/search")
                .queryParam("tags", tags.toArray(String[]::new)))
                .andExpect(status().isBadRequest());

        assertThat(repository.filters).isNull();
    }

    @Test
    void sanitizesPersistenceFailures() throws Exception {
        ProjectSearchRepository failing = new ProjectSearchRepository() {
            @Override
            public List<ProjectSummary> search(ProjectSearchFilters filters, int offset, int limit) {
                throw new DataAccessResourceFailureException("password=secret host=database");
            }

            @Override
            public long count(ProjectSearchFilters filters) {
                return 0;
            }
        };

        mockMvc(failing).perform(get("/api/v1/projects/search"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("The data store is temporarily unavailable."));
    }

    private static MockMvc mockMvc(ProjectSearchRepository repository) {
        return MockMvcBuilders
                .standaloneSetup(new ProjectSearchController(new SearchProjects(repository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static final class RecordingRepository implements ProjectSearchRepository {

        private ProjectSearchFilters filters;
        private int offset = -1;
        private int limit = -1;

        @Override
        public List<ProjectSummary> search(ProjectSearchFilters filters, int offset, int limit) {
            this.filters = filters;
            this.offset = offset;
            this.limit = limit;
            return List.of(new ProjectSummary(
                    PROJECT_ID, "eco-pack", "EcoPack Bolivia", "Envases biodegradables",
                    "Sostenibilidad", "Cochabamba", "MVP"));
        }

        @Override
        public long count(ProjectSearchFilters filters) {
            return 31;
        }
    }
}
