package com.pulso.backend.interaction.api;

import java.util.UUID;

public record ProjectInteractionMutationResponse(
        UUID projectId,
        Boolean saved,
        String feedbackType) {

    static ProjectInteractionMutationResponse saved(UUID projectId, boolean saved) {
        return new ProjectInteractionMutationResponse(projectId, saved, null);
    }

    static ProjectInteractionMutationResponse feedback(UUID projectId, String feedbackType) {
        return new ProjectInteractionMutationResponse(projectId, null, feedbackType);
    }
}
