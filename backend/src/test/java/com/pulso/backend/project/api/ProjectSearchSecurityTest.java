package com.pulso.backend.project.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.application.ProjectSearchRepository;
import com.pulso.backend.project.application.SearchProjects;
import com.pulso.backend.project.domain.ProjectSearchFilters;

@WebMvcTest(ProjectSearchController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class, ProjectSearchSecurityTest.TestBeans.class })
class ProjectSearchSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsAnonymousVisitorsToSearchPublishedProjects() throws Exception {
        mockMvc.perform(get("/api/v1/projects/search").queryParam("q", "eco"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalCount").value(0));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        SearchProjects searchProjects(ProjectSearchRepository repository) {
            return new SearchProjects(repository);
        }

        @Bean
        ProjectSearchRepository projectSearchRepository() {
            return new ProjectSearchRepository() {
                @Override
                public List<ProjectSummary> search(ProjectSearchFilters filters, int offset, int limit) {
                    return List.of();
                }

                @Override
                public long count(ProjectSearchFilters filters) {
                    return 0;
                }
            };
        }
    }
}
