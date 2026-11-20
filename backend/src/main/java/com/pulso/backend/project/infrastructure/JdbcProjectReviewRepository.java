package com.pulso.backend.project.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.project.application.ProjectReviewRepository;
import com.pulso.backend.project.domain.ProjectReviewCandidate;
import com.pulso.backend.project.domain.ProjectReviewResult;

@Repository
public class JdbcProjectReviewRepository implements ProjectReviewRepository {
    private static final String REVIEWER = """
            select id from public.profiles
            where id = :profileId and platform_role in ('MODERATOR', 'ADMIN')
              and account_status = 'ACTIVE' and deleted_at is null
            """;
    private static final String CANDIDATE = """
            select p.id, p.name, p.tagline, p.description, p.value_proposition,
                   s.name as sector, s.is_active as sector_active, p.stage, p.city, p.department,
                   p.status, p.verification_status, creator.display_name as creator_name,
                   (select count(*) from public.project_members m
                    where m.project_id = p.id and m.role = 'OWNER'
                      and m.membership_status = 'ACTIVE') as active_owner_count,
                   (p.created_by_profile_id is distinct from :reviewerId and not exists (
                       select 1 from public.project_members m
                       where m.project_id = p.id and m.profile_id = :reviewerId
                         and m.role = 'OWNER' and m.membership_status = 'ACTIVE'
                   )) as can_review,
                   p.created_at, p.published_at
            from public.projects p
            join public.sectors s on s.id = p.sector_id
            left join public.profiles creator on creator.id = p.created_by_profile_id
            """;

    private final JdbcClient jdbc;

    public JdbcProjectReviewRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean isActiveReviewer(UUID profileId) {
        return jdbc.sql(REVIEWER).param("profileId", profileId).query(UUID.class).optional().isPresent();
    }

    @Override
    public boolean lockActiveReviewer(UUID profileId) {
        return jdbc.sql(REVIEWER + " for share").param("profileId", profileId)
                .query(UUID.class).optional().isPresent();
    }

    @Override
    public List<ProjectReviewCandidate> findPending(UUID reviewerId, int offset, int limit) {
        return jdbc.sql(CANDIDATE + """
                where p.status = 'PENDING_REVIEW'
                order by p.created_at, p.id
                limit :limit offset :offset
                """)
                .param("reviewerId", reviewerId).param("offset", offset).param("limit", limit)
                .query(JdbcProjectReviewRepository::mapCandidate).list();
    }

    @Override
    public Optional<ProjectReviewCandidate> lockCandidate(UUID projectId, UUID reviewerId) {
        // Team lifecycle, membership administration and review serialize on this row.
        if (jdbc.sql("select id from public.projects where id = :id for update")
                .param("id", projectId).query(UUID.class).optional().isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql(CANDIDATE + " where p.id = :id for share of s")
                .param("id", projectId).param("reviewerId", reviewerId)
                .query(JdbcProjectReviewRepository::mapCandidate).optional();
    }

    @Override
    public int updateReview(ProjectReviewResult result) {
        return jdbc.sql("""
                update public.projects
                set status = :status, verification_status = :verification,
                    review_note = :note, reviewed_by_profile_id = :actor,
                    reviewed_at = :reviewedAt, published_at = :publishedAt
                where id = :id and status = 'PENDING_REVIEW'
                """)
                .param("id", result.projectId()).param("status", result.status())
                .param("verification", result.verificationStatus()).param("note", result.reviewNote())
                .param("actor", result.reviewedByProfileId())
                .param("reviewedAt", asTimestamp(result.reviewedAt()))
                .param("publishedAt", asTimestamp(result.publishedAt())).update();
    }

    @Override
    public void appendAudit(ProjectReviewResult result) {
        jdbc.sql("""
                insert into public.admin_audit_logs (actor_profile_id, action, target_type, target_id, metadata, created_at)
                values (:actor, :action, 'PROJECT', :projectId,
                        jsonb_build_object('fromStatus', 'PENDING_REVIEW', 'toStatus', cast(:status as text),
                                           'verificationStatus', cast(:verification as text)), :createdAt)
                """)
                .param("actor", result.reviewedByProfileId())
                .param("action", "PUBLISHED".equals(result.status()) ? "PROJECT_APPROVED" : "PROJECT_REJECTED")
                .param("projectId", result.projectId()).param("status", result.status())
                .param("verification", result.verificationStatus())
                .param("createdAt", asTimestamp(result.reviewedAt())).update();
    }

    private static ProjectReviewCandidate mapCandidate(ResultSet row, int index) throws SQLException {
        return new ProjectReviewCandidate(
                row.getObject("id", UUID.class), row.getString("name"), row.getString("tagline"),
                row.getString("description"), row.getString("value_proposition"), row.getString("sector"),
                row.getBoolean("sector_active"), row.getString("stage"), row.getString("city"),
                row.getString("department"), row.getString("status"), row.getString("verification_status"),
                row.getString("creator_name"), row.getInt("active_owner_count"), row.getBoolean("can_review"),
                instant(row, "created_at"), instant(row, "published_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime asTimestamp(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
