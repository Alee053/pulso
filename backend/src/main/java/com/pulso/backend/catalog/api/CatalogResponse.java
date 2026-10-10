package com.pulso.backend.catalog.api;

import java.util.List;
import java.util.UUID;

import com.pulso.backend.catalog.domain.ActiveCatalogs;
import com.pulso.backend.catalog.domain.SectorCatalogItem;
import com.pulso.backend.catalog.domain.SkillCatalogItem;
import com.pulso.backend.catalog.domain.TagCatalogItem;
import com.pulso.backend.preference.domain.OnboardingOptions;

public record CatalogResponse(Data data) {

    public static CatalogResponse from(ActiveCatalogs catalogs) {
        return new CatalogResponse(new Data(
                catalogs.sectors().stream().map(SectorResponse::from).toList(),
                catalogs.tags().stream().map(TagResponse::from).toList(),
                catalogs.skills().stream().map(SkillResponse::from).toList(),
                OnboardingOptions.INTENTIONS,
                OnboardingOptions.STAGES));
    }

    public record Data(
            List<SectorResponse> sectors,
            List<TagResponse> tags,
            List<SkillResponse> skills,
            List<String> intentions,
            List<String> stages) {
    }

    public record SectorResponse(
            UUID id,
            String slug,
            String name,
            String description) {

        static SectorResponse from(SectorCatalogItem sector) {
            return new SectorResponse(
                    sector.id(),
                    sector.slug(),
                    sector.name(),
                    sector.description());
        }
    }

    public record TagResponse(
            UUID id,
            String slug,
            String name,
            String type) {

        static TagResponse from(TagCatalogItem tag) {
            return new TagResponse(
                    tag.id(),
                    tag.slug(),
                    tag.name(),
                    tag.type());
        }
    }

    public record SkillResponse(
            UUID id,
            String slug,
            String name,
            String category) {

        static SkillResponse from(SkillCatalogItem skill) {
            return new SkillResponse(
                    skill.id(),
                    skill.slug(),
                    skill.name(),
                    skill.category());
        }
    }
}
