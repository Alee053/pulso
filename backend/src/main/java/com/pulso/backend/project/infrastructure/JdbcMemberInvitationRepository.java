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
import com.pulso.backend.project.application.MemberInvitationConflictException;
import com.pulso.backend.project.application.MemberInvitationRepository;
import com.pulso.backend.project.application.StoredMemberInvitation;
import com.pulso.backend.project.domain.CreatedMemberInvitation;
import com.pulso.backend.project.domain.DuplicateMemberInvitationException;
import com.pulso.backend.project.domain.MemberInvitation;
import com.pulso.backend.project.domain.MemberInvitationActor;

@Repository
public class JdbcMemberInvitationRepository implements MemberInvitationRepository {
    private final JdbcClient jdbc;
    private final MemberInvitation.Limits invitationLimits;
    public JdbcMemberInvitationRepository(JdbcClient jdbc, ProjectProperties properties) {
        this.jdbc = jdbc;
        this.invitationLimits = new MemberInvitation.Limits(properties.inviteeEmailMaxLength());
    }

    @Override public boolean projectExists(UUID id) {
        return jdbc.sql("select exists(select 1 from public.projects where id = :id)")
                .param("id", id).query(Boolean.class).single();
    }

    @Override public Optional<MemberInvitationActor> findActor(UUID projectId, UUID profileId) {
        return jdbc.sql("""
                select project_id, profile_id, role, membership_status
                from public.project_members where project_id = :projectId and profile_id = :profileId
                """).param("projectId", projectId).param("profileId", profileId)
                .query((r, i) -> new MemberInvitationActor(
                        r.getObject("project_id", UUID.class), r.getObject("profile_id", UUID.class),
                        r.getString("role"), r.getString("membership_status"))).optional();
    }

    @Override public List<MemberInvitation> findMembers(UUID projectId) {
        return jdbc.sql("""
                select project_id, invitee_email, profile_id, role, membership_status, created_by_profile_id
                from public.project_members where project_id = :projectId
                """).param("projectId", projectId).query((r, i) -> mapInvitation(r, i)).list();
    }

    // The default local/backend datasource is postgres, which can read auth.users.
    // A restricted production database role needs a dedicated SECURITY DEFINER lookup instead.
    @Override public boolean isActiveAccountEmail(UUID projectId, String email) {
        return jdbc.sql("""
                select exists (
                  select 1 from public.project_members m
                  join auth.users u on u.id = m.profile_id
                  where m.project_id = :projectId and m.membership_status = 'ACTIVE'
                    and lower(trim(u.email)) = :email
                )
                """).param("projectId", projectId).param("email", email).query(Boolean.class).single();
    }

    @Override public UUID save(CreatedMemberInvitation created) {
        MemberInvitation invitation = created.invitation();
        try {
            return jdbc.sql("""
                    insert into public.project_members
                      (project_id, invitee_email, invite_token_digest, role, membership_status, created_by_profile_id)
                    values (:projectId, :email, :digest, :role, :status, :actor)
                    returning id
                    """).param("projectId", invitation.projectId())
                    .param("email", invitation.inviteeEmail())
                    .param("digest", created.inviteTokenDigest())
                    .param("role", invitation.role())
                    .param("status", invitation.membershipStatus())
                    .param("actor", invitation.createdByProfileId())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException ex) { throw new DuplicateMemberInvitationException(); }
    }

    @Override public Optional<StoredMemberInvitation> findByDigest(String digest) {
        return jdbc.sql("""
                select id, project_id, invitee_email, profile_id, role, membership_status, created_by_profile_id
                from public.project_members where invite_token_digest = :digest
                """).param("digest", digest).query((r,i) ->
                        new StoredMemberInvitation(r.getObject("id", UUID.class), mapInvitation(r,i))).optional();
    }

    @Override public boolean hasMembership(UUID projectId, UUID profileId) {
        return jdbc.sql("""
                select exists(select 1 from public.project_members
                where project_id = :projectId and profile_id = :profileId)
                """).param("projectId", projectId).param("profileId", profileId).query(Boolean.class).single();
    }

    @Override public boolean activate(UUID invitationId, UUID profileId) {
        try {
            return !jdbc.sql("""
                    update public.project_members
                    set profile_id = :profileId, membership_status = 'ACTIVE',
                        invite_token_digest = null, joined_at = timezone('utc', now())
                    where id = :invitationId and membership_status = 'INVITED' and profile_id is null
                    returning id
                    """).param("invitationId", invitationId).param("profileId", profileId)
                    .query(UUID.class).list().isEmpty();
        } catch (DuplicateKeyException ex) { throw new MemberInvitationConflictException(); }
    }

    private MemberInvitation mapInvitation(ResultSet r, int i) throws SQLException {
        return new MemberInvitation(
            r.getObject("project_id", UUID.class),
            r.getString("invitee_email"),
            r.getObject("profile_id", UUID.class),
            r.getString("role"),
            r.getString("membership_status"),
            r.getObject("created_by_profile_id", UUID.class),
            invitationLimits
        );
    }
}
