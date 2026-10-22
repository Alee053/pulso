package com.pulso.backend.opportunity.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.opportunity.application.OpportunitySkillRepository;
import com.pulso.backend.opportunity.domain.OpportunitySkill;

@Repository
public class JdbcOpportunitySkillRepository implements OpportunitySkillRepository {

    private final JdbcClient jdbcClient;

    public JdbcOpportunitySkillRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<OpportunitySkill> list(UUID projectId, UUID opportunityId) {
        return jdbcClient.sql("""
                select association.opportunity_id,
                       skill.id as skill_id,
                       skill.slug as skill_slug,
                       skill.name as skill_name,
                       association.importance,
                       skill.is_active,
                       association.created_at
                from public.opportunity_skills association
                join public.project_opportunities opportunity
                  on opportunity.id = association.opportunity_id
                join public.skills skill on skill.id = association.skill_id
                where opportunity.project_id = :projectId
                  and association.opportunity_id = :opportunityId
                order by association.importance asc, skill.name asc, skill.id asc
                """)
                .param("projectId", projectId)
                .param("opportunityId", opportunityId)
                .query(JdbcOpportunitySkillRepository::mapSkill)
                .list();
    }

    @Override
    public boolean isActiveSkill(UUID skillId) {
        return jdbcClient.sql("""
                select exists (
                    select 1 from public.skills
                    where id = :skillId and is_active = true
                )
                """)
                .param("skillId", skillId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<OpportunitySkill> create(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            String importance,
            UUID profileId) {
        return jdbcClient.sql("""
                with inserted as (
                    insert into public.opportunity_skills (
                        opportunity_id, skill_id, importance
                    )
                    select opportunity.id, skill.id, :importance
                    from public.project_opportunities opportunity
                    join public.skills skill
                      on skill.id = :skillId and skill.is_active = true
                    where opportunity.id = :opportunityId
                      and opportunity.project_id = :projectId
                      and exists (
                          select 1
                          from public.project_members member
                          where member.project_id = opportunity.project_id
                            and member.profile_id = :profileId
                            and member.role in ('OWNER', 'ADMIN')
                            and member.membership_status = 'ACTIVE'
                      )
                    on conflict (opportunity_id, skill_id) do nothing
                    returning opportunity_id, skill_id, importance, created_at
                )
                select inserted.opportunity_id,
                       skill.id as skill_id,
                       skill.slug as skill_slug,
                       skill.name as skill_name,
                       inserted.importance,
                       skill.is_active,
                       inserted.created_at
                from inserted
                join public.skills skill on skill.id = inserted.skill_id
                """)
                .param("projectId", projectId)
                .param("opportunityId", opportunityId)
                .param("skillId", skillId)
                .param("importance", importance)
                .param("profileId", profileId)
                .query(JdbcOpportunitySkillRepository::mapSkill)
                .optional();
    }

    @Override
    public Optional<OpportunitySkill> updateImportance(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            String importance,
            UUID profileId) {
        return jdbcClient.sql("""
                with updated as (
                    update public.opportunity_skills association
                    set importance = :importance
                    from public.project_opportunities opportunity
                    where association.opportunity_id = opportunity.id
                      and opportunity.project_id = :projectId
                      and opportunity.id = :opportunityId
                      and association.skill_id = :skillId
                      and exists (
                          select 1
                          from public.project_members member
                          where member.project_id = opportunity.project_id
                            and member.profile_id = :profileId
                            and member.role in ('OWNER', 'ADMIN')
                            and member.membership_status = 'ACTIVE'
                      )
                    returning association.opportunity_id,
                              association.skill_id,
                              association.importance,
                              association.created_at
                )
                select updated.opportunity_id,
                       skill.id as skill_id,
                       skill.slug as skill_slug,
                       skill.name as skill_name,
                       updated.importance,
                       skill.is_active,
                       updated.created_at
                from updated
                join public.skills skill on skill.id = updated.skill_id
                """)
                .param("projectId", projectId)
                .param("opportunityId", opportunityId)
                .param("skillId", skillId)
                .param("importance", importance)
                .param("profileId", profileId)
                .query(JdbcOpportunitySkillRepository::mapSkill)
                .optional();
    }

    @Override
    public boolean delete(
            UUID projectId,
            UUID opportunityId,
            UUID skillId,
            UUID profileId) {
        return jdbcClient.sql("""
                delete from public.opportunity_skills association
                using public.project_opportunities opportunity
                where association.opportunity_id = opportunity.id
                  and opportunity.project_id = :projectId
                  and opportunity.id = :opportunityId
                  and association.skill_id = :skillId
                  and exists (
                      select 1
                      from public.project_members member
                      where member.project_id = opportunity.project_id
                        and member.profile_id = :profileId
                        and member.role in ('OWNER', 'ADMIN')
                        and member.membership_status = 'ACTIVE'
                  )
                """)
                .param("projectId", projectId)
                .param("opportunityId", opportunityId)
                .param("skillId", skillId)
                .param("profileId", profileId)
                .update() == 1;
    }

    private static OpportunitySkill mapSkill(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OpportunitySkill(
                resultSet.getObject("opportunity_id", UUID.class),
                resultSet.getObject("skill_id", UUID.class),
                resultSet.getString("skill_slug"),
                resultSet.getString("skill_name"),
                resultSet.getString("importance"),
                resultSet.getBoolean("is_active"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class));
    }
}
