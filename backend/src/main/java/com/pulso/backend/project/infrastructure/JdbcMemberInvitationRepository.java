package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.application.MemberInvitationRepository;
import com.pulso.backend.project.application.PersistedMemberInvitation;
import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.DuplicateMemberInvitationException;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;

@Repository
public class JdbcMemberInvitationRepository implements MemberInvitationRepository {

    private final JdbcClient jdbcClient;
    private final MemberInvitation.Limits limits;

    public JdbcMemberInvitationRepository(
            JdbcClient jdbcClient,
            ProjectProperties properties) {
        this.jdbcClient = jdbcClient;
        this.limits = new MemberInvitation.Limits(properties.inviteeEmailMaxLength());
    }

    @Override
    public boolean projectExists(UUID projectId) {
        return jdbcClient.sql("""
                select exists(
                    select 1
                    from public.projects
                    where id = :projectId
                )
                """)
                .param("projectId", projectId)
                .query((resultSet, rowNumber) -> resultSet.getBoolean(1))
                .single();
    }

    @Override
    public Optional<MemberInvitationActor> findActor(UUID projectId, UUID profileId) {
        return jdbcClient.sql("""
                select role, membership_status
                from public.project_members
                where project_id = :projectId
                  and profile_id = :profileId
                """)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .query((resultSet, rowNumber) -> new MemberInvitationActor(
                        projectId,
                        profileId,
                        resultSet.getString("role"),
                        resultSet.getString("membership_status")))
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public List<MemberInvitation> findInvitations(UUID projectId) {
        return jdbcClient.sql("""
                select project_id, invitee_email, profile_id, role,
                       membership_status, created_by_profile_id
                from public.project_members
                where project_id = :projectId
                  and invitee_email is not null
                order by id
                """)
                .param("projectId", projectId)
                .query((resultSet, rowNumber) -> mapInvitation(resultSet, limits))
                .list();
    }

    @Override
    public PersistedMemberInvitation createInvitation(CreatedMemberInvitation created) {
        try {
            return jdbcClient.sql("""
                    insert into public.project_members (
                        project_id,
                        invitee_email,
                        invite_token_digest,
                        role,
                        membership_status,
                        created_by_profile_id
                    ) values (
                        :projectId,
                        :inviteeEmail,
                        :inviteTokenDigest,
                        'MEMBER',
                        'INVITED',
                        :createdByProfileId
                    )
                    returning
                        id,
                        project_id,
                        invitee_email,
                        profile_id,
                        role,
                        membership_status,
                        created_by_profile_id
                    """)
                    .param("projectId", created.invitation().projectId())
                    .param("inviteeEmail", created.invitation().inviteeEmail())
                    .param("inviteTokenDigest", created.inviteTokenDigest())
                    .param("createdByProfileId", created.invitation().createdByProfileId())
                    .query((resultSet, rowNumber) -> mapPersistedInvitation(resultSet, limits))
                    .single();
        } catch (DuplicateKeyException exception) {
            throw new DuplicateMemberInvitationException();
        }
    }

    private static PersistedMemberInvitation mapPersistedInvitation(
            ResultSet resultSet,
            MemberInvitation.Limits limits) throws SQLException {
        return new PersistedMemberInvitation(
                resultSet.getObject("id", UUID.class),
                mapInvitation(resultSet, limits));
    }

    private static MemberInvitation mapInvitation(
            ResultSet resultSet,
            MemberInvitation.Limits limits) throws SQLException {
        return new MemberInvitation(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("invitee_email"),
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("role"),
                resultSet.getString("membership_status"),
                resultSet.getObject("created_by_profile_id", UUID.class),
                limits);
    }
}
