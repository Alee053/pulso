package com.pulso.backend.interaction.api;

import java.util.List;
import java.util.UUID;

import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.interaction.domain.ProjectPage;

public record DiscoveryFeedResponse(
        List<Item> data,
        ProjectCollectionResponse.PageMetadata meta) {

    static DiscoveryFeedResponse from(ProjectPage<DiscoveryFeedItem> page) {
        return new DiscoveryFeedResponse(
                page.data().stream().map(Item::from).toList(),
                new ProjectCollectionResponse.PageMetadata(
                        page.offset(), page.limit(), page.hasMore(), page.totalCount()));
    }

    public record Item(
            UUID id,
            String slug,
            String name,
            String tagline,
            String sector,
            String city,
            String stage,
            boolean saved,
            String feedbackType) {

        static Item from(DiscoveryFeedItem item) {
            var project = item.project();
            ProjectFeedbackType feedback = item.feedbackType();
            return new Item(
                    project.id(),
                    project.slug(),
                    project.name(),
                    project.tagline(),
                    project.sector(),
                    project.city(),
                    project.stage(),
                    item.saved(),
                    feedback == null ? null : feedback.name());
        }
    }
}
