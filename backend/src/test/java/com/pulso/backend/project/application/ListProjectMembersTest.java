package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.MembershipPermissionException;
import com.pulso.backend.project.domain.ProjectMemberSummary;

class ListProjectMembersTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();

    @Test
    void returnsThePersistedActorAndAuthorizedPageFromTheSameRead() {
        ProjectMemberSummary member = new ProjectMemberSummary(
                UUID.randomUUID(), PROJECT_ID, PROFILE_ID, "MEMBER", "ACTIVE", "Integrante", "Equipo");
        ProjectMembersSnapshot snapshot = new ProjectMembersSnapshot(
                new MembershipActor(PROJECT_ID, PROFILE_ID, "MEMBER", "ACTIVE"), List.of(member));
        ListProjectMembers useCase = new ListProjectMembers((projectId, profileId, offset, limit) -> {
            assertThat(projectId).isEqualTo(PROJECT_ID);
            assertThat(profileId).isEqualTo(PROFILE_ID);
            assertThat(offset).isEqualTo(20);
            assertThat(limit).isEqualTo(21);
            return Optional.of(snapshot);
        });

        assertThat(useCase.execute(PROJECT_ID, PROFILE_ID, 20, 21)).isEqualTo(snapshot);
    }

    @Test
    void deniesAnAbsentAuthorizedSnapshot() {
        ListProjectMembers useCase = new ListProjectMembers((projectId, profileId, offset, limit) -> Optional.empty());

        assertThatThrownBy(() -> useCase.execute(PROJECT_ID, PROFILE_ID, 0, 21))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("active project membership is required.");
    }
}
