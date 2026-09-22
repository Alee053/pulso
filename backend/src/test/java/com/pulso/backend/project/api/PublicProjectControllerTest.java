package com.pulso.backend.project.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.project.application.GetPublicProjectDetail;
import com.pulso.backend.project.application.PublicProjectRepository;
import com.pulso.backend.project.domain.PublicProjectDetail;

class PublicProjectControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID SECTOR_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID TAG_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID PROFILE_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void returnsOnlyThePublicProjectContract() throws Exception {
        mockMvc(reference -> Optional.of(project())).perform(get("/api/v1/projects/eco-pack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.data.slug").value("eco-pack"))
                .andExpect(jsonPath("$.data.valueProposition")
                        .value("Reduce residuos agrícolas."))
                .andExpect(jsonPath("$.data.sector.id").value(SECTOR_ID.toString()))
                .andExpect(jsonPath("$.data.sector.slug").value("sostenibilidad"))
                .andExpect(jsonPath("$.data.tags[0].id").value(TAG_ID.toString()))
                .andExpect(jsonPath("$.data.externalLinks[0].type").value("WEBSITE"))
                .andExpect(jsonPath("$.data.members[0].profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.data.members[0].isFeatured").value(true))
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].inviteeEmail").doesNotExist());
    }

    @Test
    void returnsTheSameNotFoundResponseForMissingOrUnpublishedProjects() throws Exception {
        mockMvc(reference -> Optional.empty()).perform(get("/api/v1/projects/not-public"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Public project not found"))
                .andExpect(jsonPath("$.detail")
                        .value("The project does not exist or is not publicly available."));
    }

    @Test
    void sanitizesPersistenceFailures() throws Exception {
        mockMvc(reference -> {
            throw new DataAccessResourceFailureException("password=secret host=database");
        }).perform(get("/api/v1/projects/eco-pack"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Data store unavailable"))
                .andExpect(jsonPath("$.detail")
                        .value("The data store is temporarily unavailable."));
    }

    private static MockMvc mockMvc(PublicProjectRepository repository) {
        return MockMvcBuilders
                .standaloneSetup(new PublicProjectController(
                        new GetPublicProjectDetail(repository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static PublicProjectDetail project() {
        return new PublicProjectDetail(
                PROJECT_ID,
                "eco-pack",
                "EcoPack Bolivia",
                "Envases biodegradables",
                "Envases compostables.",
                "Reduce residuos agrícolas.",
                new PublicProjectDetail.Sector(SECTOR_ID, "sostenibilidad", "Sostenibilidad"),
                "MVP",
                "Cochabamba",
                "Cochabamba",
                List.of(new PublicProjectDetail.Tag(TAG_ID, "economia-circular", "Economía circular")),
                List.of(new PublicProjectDetail.ExternalLink(
                        "WEBSITE",
                        "https://ecopack.example",
                        "Sitio web")),
                List.of(new PublicProjectDetail.Member(
                        PROFILE_ID,
                        "Persona Uno",
                        "avatars/persona-uno.webp",
                        "Co-fundadora",
                        true)));
    }
}
