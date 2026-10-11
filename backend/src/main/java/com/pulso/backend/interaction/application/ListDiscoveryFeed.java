package com.pulso.backend.interaction.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.DiscoveryFeedCursor;
import com.pulso.backend.interaction.domain.DiscoveryFeedPage;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.domain.OnboardingPreferences;

@Service
public class ListDiscoveryFeed {

    private final ProjectInteractionRepository repository;
    private final OnboardingPreferencesRepository preferencesRepository;

    public ListDiscoveryFeed(
            ProjectInteractionRepository repository,
            OnboardingPreferencesRepository preferencesRepository) {
        this.repository = repository;
        this.preferencesRepository = preferencesRepository;
    }

    @Transactional(readOnly = true)
    public DiscoveryFeedPage execute(
            UUID profileId,
            int offset,
            DiscoveryFeedCursor cursor,
            int limit) {
        var preferences = preferencesRepository.findByProfileId(profileId);
        List<String> preferredSectors = preferences
                .map(OnboardingPreferences::preferredSectors)
                .orElseGet(List::of);
        List<String> preferredStages = preferences
                .map(OnboardingPreferences::preferredStages)
                .orElseGet(List::of);
        List<DiscoveryFeedItem> rows = repository.findDiscoveryFeed(
                profileId,
                preferredSectors,
                preferredStages,
                offset,
                cursor,
                limit + 1);
        boolean hasMore = rows.size() > limit;
        List<DiscoveryFeedItem> data = rows.stream().limit(limit).toList();
        String nextCursor = null;
        if (hasMore && !data.isEmpty()) {
            DiscoveryFeedItem last = data.get(data.size() - 1);
            int nextOffset = (cursor == null ? offset : cursor.offset()) + data.size();
            nextCursor = new DiscoveryFeedCursor(
                    last.preferenceScore(),
                    last.publishedAt(),
                    last.project().id(),
                    nextOffset).encode();
        }
        return new DiscoveryFeedPage(
                data,
                cursor == null ? offset : cursor.offset(),
                limit,
                hasMore,
                repository.countDiscoveryFeed(profileId),
                nextCursor);
    }
}
