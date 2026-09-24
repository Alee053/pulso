package com.pulso.backend.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.pulso.backend.catalog.domain.ActiveCatalogs;
import com.pulso.backend.catalog.domain.SectorCatalogItem;
import com.pulso.backend.catalog.domain.SkillCatalogItem;
import com.pulso.backend.catalog.domain.TagCatalogItem;
import com.pulso.backend.config.CatalogProperties;

class ListActiveCatalogsTest {

    private static final CatalogProperties PROPERTIES = new CatalogProperties(500);

    @Test
    void returnsAllCatalogsUsingTheBoundedRepositoryQueries() {
        SectorCatalogItem sector = new SectorCatalogItem(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "tecnologia",
                "Tecnología",
                "Productos y servicios tecnológicos.");
        TagCatalogItem tag = new TagCatalogItem(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "inteligencia-artificial",
                "Inteligencia artificial",
                "TOPIC");
        SkillCatalogItem skill = new SkillCatalogItem(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "java",
                "Java",
                "DESARROLLO");
        CapturingRepository repository = new CapturingRepository(
                List.of(sector), List.of(tag), List.of(skill));

        ActiveCatalogs result = new ListActiveCatalogs(repository, PROPERTIES).execute();

        assertThat(result.sectors()).containsExactly(sector);
        assertThat(result.tags()).containsExactly(tag);
        assertThat(result.skills()).containsExactly(skill);
        assertThat(repository.requestedLimits)
                .containsExactly(
                        PROPERTIES.maxItemsPerCatalog(),
                        PROPERTIES.maxItemsPerCatalog(),
                        PROPERTIES.maxItemsPerCatalog());
    }

    @Test
    void returnsEmptyListsWhenThereAreNoActiveCatalogEntries() {
        ActiveCatalogs result = new ListActiveCatalogs(
                new CapturingRepository(List.of(), List.of(), List.of()),
                PROPERTIES)
                .execute();

        assertThat(result.sectors()).isEmpty();
        assertThat(result.tags()).isEmpty();
        assertThat(result.skills()).isEmpty();
    }

    @Test
    void propagatesRepositoryFailuresToTheApiBoundary() {
        ActiveCatalogRepository repository = new ActiveCatalogRepository() {
            @Override
            public List<SectorCatalogItem> findActiveSectors(int limit) {
                throw new DataAccessResourceFailureException("database unavailable");
            }

            @Override
            public List<TagCatalogItem> findActiveTags(int limit) {
                return List.of();
            }

            @Override
            public List<SkillCatalogItem> findActiveSkills(int limit) {
                return List.of();
            }
        };

        assertThatThrownBy(() -> new ListActiveCatalogs(repository, PROPERTIES).execute())
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("database unavailable");
    }

    private static final class CapturingRepository implements ActiveCatalogRepository {

        private final List<SectorCatalogItem> sectors;
        private final List<TagCatalogItem> tags;
        private final List<SkillCatalogItem> skills;
        private final java.util.ArrayList<Integer> requestedLimits = new java.util.ArrayList<>();

        private CapturingRepository(
                List<SectorCatalogItem> sectors,
                List<TagCatalogItem> tags,
                List<SkillCatalogItem> skills) {
            this.sectors = sectors;
            this.tags = tags;
            this.skills = skills;
        }

        @Override
        public List<SectorCatalogItem> findActiveSectors(int limit) {
            requestedLimits.add(limit);
            return sectors;
        }

        @Override
        public List<TagCatalogItem> findActiveTags(int limit) {
            requestedLimits.add(limit);
            return tags;
        }

        @Override
        public List<SkillCatalogItem> findActiveSkills(int limit) {
            requestedLimits.add(limit);
            return skills;
        }
    }
}
