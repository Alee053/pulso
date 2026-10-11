package com.pulso.backend.interaction.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.project.domain.ProjectValidationException;

class SetSavedProjectTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    private final ProjectInteractionRepository repository = mock(ProjectInteractionRepository.class);
    private final SetSavedProject useCase = new SetSavedProject(repository);

    @Test
    void delegatesSaveAndRemoveCommandsToTheRepository() {
        useCase.execute(PROFILE_ID, PROJECT_ID, true);
        useCase.execute(PROFILE_ID, PROJECT_ID, false);

        verify(repository).setSaved(PROFILE_ID, PROJECT_ID, true);
        verify(repository).setSaved(PROFILE_ID, PROJECT_ID, false);
    }

    @Test
    void requiresTheAuthenticatedProfileAndProjectIdentifiers() {
        assertThatThrownBy(() -> useCase.execute(null, PROJECT_ID, true))
                .isInstanceOf(ProjectValidationException.class);
        assertThatThrownBy(() -> useCase.execute(PROFILE_ID, null, false))
                .isInstanceOf(ProjectValidationException.class);

        verifyNoInteractions(repository);
    }
}
