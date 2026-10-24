package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.domain.ProjectSearchFilters;

class SearchProjectsTest {

    private static final ProjectSearchFilters FILTERS = new ProjectSearchFilters(
            "eco", null, "MVP", null, List.of(), true);

    @Test
    void returnsThePageTogetherWithTheTotalOfMatches() {
        ProjectSummary summary = new ProjectSummary(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "eco-pack", "EcoPack", "Envases", "Sostenibilidad", "Cochabamba", "MVP");

        SearchProjects.SearchResult result = new SearchProjects(new ProjectSearchRepository() {
            @Override
            public List<ProjectSummary> search(ProjectSearchFilters filters, int offset, int limit) {
                assertThat(filters).isEqualTo(FILTERS);
                assertThat(offset).isEqualTo(20);
                assertThat(limit).isEqualTo(10);
                return List.of(summary);
            }

            @Override
            public long count(ProjectSearchFilters filters) {
                assertThat(filters).isEqualTo(FILTERS);
                return 31;
            }
        }).execute(FILTERS, 20, 10);

        assertThat(result.data()).containsExactly(summary);
        assertThat(result.totalCount()).isEqualTo(31);
    }
}
