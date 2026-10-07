package com.pulso.backend.project.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.application.CurrentProjectsRepository;
import com.pulso.backend.project.application.ListCurrentProjects;
import com.pulso.backend.project.domain.CurrentProject;

@WebMvcTest(CurrentProjectsController.class)
@Import({SecurityConfig.class, ListCurrentProjects.class})
class CurrentProjectsControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentProjectsRepository repository;

    @Test
    void requiresAnAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/v1/me/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidSubjectAndPagination() throws Exception {
        mockMvc.perform(get("/api/v1/me/projects")
                .with(jwt().jwt(token -> token.subject("not-a-uuid"))))
                .andExpect(status().isUnauthorized());
        for (String query : List.of("limit=0", "limit=51", "offset=-1", "offset=100001")) {
            mockMvc.perform(get("/api/v1/me/projects?" + query)
                    .with(jwt().jwt(token -> token.subject(PROFILE_ID.toString()))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void returnsAnEmptyPageWithoutLeakingAnotherActorsData() throws Exception {
        when(repository.findActive(PROFILE_ID, 0, 21)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/me/projects")
                .with(jwt().jwt(token -> token.subject(PROFILE_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.hasMore").value(false));
    }

    @Test
    void listsOnlyTheAuthenticatedActorsProjectsWithBoundedPagination() throws Exception {
        when(repository.findActive(PROFILE_ID, 0, 2)).thenReturn(List.of(
                new CurrentProject(PROJECT_ID, "EcoPack", "OWNER", "ACTIVE"),
                new CurrentProject(UUID.randomUUID(), "AndeSolar", "MEMBER", "ACTIVE")));

        mockMvc.perform(get("/api/v1/me/projects?offset=0&limit=1")
                .with(jwt().jwt(token -> token.subject(PROFILE_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.data[0].name").value("EcoPack"))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].profileId").doesNotExist())
                .andExpect(jsonPath("$.data[0].inviteeEmail").doesNotExist())
                .andExpect(jsonPath("$.meta.hasMore").value(true));

        verify(repository).findActive(eq(PROFILE_ID), eq(0), eq(2));
    }
}
