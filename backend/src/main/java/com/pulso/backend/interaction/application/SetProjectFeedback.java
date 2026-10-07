package com.pulso.backend.interaction.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.project.domain.ProjectValidationException;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.domain.RecommendationWeights;

@Service
public class SetProjectFeedback {

    private final ProjectInteractionRepository repository;
    private final OnboardingPreferencesRepository preferencesRepository;

    public SetProjectFeedback(
            ProjectInteractionRepository repository,
            OnboardingPreferencesRepository preferencesRepository) {
        this.repository = repository;
        this.preferencesRepository = preferencesRepository;
    }

    @Transactional
    public void execute(
            UUID profileId,
            UUID projectId,
            ProjectFeedbackType feedbackType,
            Map<String, Double> recommendationWeights) {
        if (profileId == null || projectId == null || feedbackType == null) {
            throw new ProjectValidationException(
                    "profileId, projectId, and feedbackType are required.");
        }
        Map<String, Double> weights = recommendationWeights == null
                ? null
                : RecommendationWeights.normalize(recommendationWeights);
        repository.setFeedback(profileId, projectId, feedbackType);
        if (weights != null) {
            preferencesRepository.saveRecommendationWeights(profileId, weights);
        }
    }
}
