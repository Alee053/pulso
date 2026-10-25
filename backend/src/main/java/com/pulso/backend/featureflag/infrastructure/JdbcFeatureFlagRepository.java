package com.pulso.backend.featureflag.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.pulso.backend.featureflag.application.FeatureFlagRepository;
import com.pulso.backend.featureflag.domain.FeatureFlag;
import com.pulso.backend.featureflag.domain.FeatureFlagKey;

@Repository
public class JdbcFeatureFlagRepository implements FeatureFlagRepository {

    private final JdbcClient jdbcClient;

    public JdbcFeatureFlagRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<FeatureFlag> findAll() {
        return jdbcClient.sql("""
                select flag_key, enabled
                from public.feature_flags
                order by flag_key
                """)
                .query(JdbcFeatureFlagRepository::mapFlag)
                .list();
    }

    @Override
    public Optional<FeatureFlag> findByKey(FeatureFlagKey key) {
        return jdbcClient.sql("""
                select flag_key, enabled
                from public.feature_flags
                where flag_key = :key
                """)
                .param("key", key.key())
                .query(JdbcFeatureFlagRepository::mapFlag)
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public boolean isEnabled(FeatureFlagKey key) {
        return findByKey(key)
                .map(FeatureFlag::enabled)
                .orElseGet(key::defaultEnabled);
    }

    @Override
    public Optional<FeatureFlag> update(FeatureFlagKey key, boolean enabled) {
        return jdbcClient.sql("""
                update public.feature_flags
                set enabled = :enabled
                where flag_key = :key
                returning flag_key, enabled
                """)
                .param("key", key.key())
                .param("enabled", enabled)
                .query(JdbcFeatureFlagRepository::mapFlag)
                .list()
                .stream()
                .findFirst();
    }

    private static FeatureFlag mapFlag(ResultSet resultSet, int rowNumber) throws SQLException {
        FeatureFlagKey key = FeatureFlagKey.from(resultSet.getString("flag_key"))
                .orElseThrow(() -> new SQLException("Unknown persisted feature flag."));
        return new FeatureFlag(key, resultSet.getBoolean("enabled"));
    }
}
