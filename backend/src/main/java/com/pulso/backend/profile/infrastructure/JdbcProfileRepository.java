package com.pulso.backend.profile.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.profile.application.ProfileRepository;
import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfilePatch;

@Repository
public class JdbcProfileRepository implements ProfileRepository {

    private static final String PROFILE_COLUMNS = """
            id,
            profile_kind,
            display_name,
            avatar_storage_path,
            short_bio,
            city,
            department,
            profile_visibility,
            verification_status,
            account_status,
            platform_role,
            onboarding_completed_at,
            created_at,
            updated_at
            """;

    private final JdbcClient jdbcClient;

    public JdbcProfileRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Profile> findById(UUID profileId) {
        return jdbcClient.sql("""
                select %s
                from public.profiles
                where id = :id
                """.formatted(PROFILE_COLUMNS))
                .param("id", profileId)
                .query(JdbcProfileRepository::mapProfile)
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Profile> update(UUID profileId, ProfilePatch patch) {
        return jdbcClient.sql("""
                update public.profiles
                set profile_kind = coalesce(:profileKind, profile_kind),
                    display_name = coalesce(:displayName, display_name),
                    avatar_storage_path = coalesce(:avatarStoragePath, avatar_storage_path),
                    short_bio = coalesce(:shortBio, short_bio),
                    city = coalesce(:city, city),
                    department = coalesce(:department, department),
                    profile_visibility = coalesce(:profileVisibility, profile_visibility),
                    onboarding_completed_at = coalesce(
                        :onboardingCompletedAt,
                        onboarding_completed_at)
                where id = :id
                returning %s
                """.formatted(PROFILE_COLUMNS))
                .param("profileKind", patch.profileKind())
                .param("displayName", patch.displayName())
                .param("avatarStoragePath", patch.avatarStoragePath())
                .param("shortBio", patch.shortBio())
                .param("city", patch.city())
                .param("department", patch.department())
                .param("profileVisibility", patch.profileVisibility())
                .param("onboardingCompletedAt", patch.onboardingCompletedAt())
                .param("id", profileId)
                .query(JdbcProfileRepository::mapProfile)
                .list()
                .stream()
                .findFirst();
    }

    private static Profile mapProfile(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Profile(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("profile_kind"),
                resultSet.getString("display_name"),
                resultSet.getString("avatar_storage_path"),
                resultSet.getString("short_bio"),
                resultSet.getString("city"),
                resultSet.getString("department"),
                resultSet.getString("profile_visibility"),
                resultSet.getString("verification_status"),
                resultSet.getString("account_status"),
                resultSet.getString("platform_role"),
                resultSet.getObject("onboarding_completed_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }
}
