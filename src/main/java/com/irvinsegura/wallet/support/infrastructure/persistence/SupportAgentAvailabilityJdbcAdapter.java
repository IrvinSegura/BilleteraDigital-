package com.irvinsegura.wallet.support.infrastructure.persistence;

import com.irvinsegura.wallet.support.domain.port.SupportAgentAvailabilityRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SupportAgentAvailabilityJdbcAdapter implements SupportAgentAvailabilityRepository {

    private final JdbcClient jdbcClient;

    SupportAgentAvailabilityJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<UUID> findPaused(Collection<UUID> agents) {
        if (agents.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbcClient.sql(
                        "SELECT agent_id FROM support_agent_availability WHERE NOT available AND agent_id IN (:agents)")
                .param("agents", agents)
                .query((rs, rowNum) -> rs.getObject("agent_id", UUID.class))
                .list());
    }

    @Override
    public void save(UUID agentId, boolean available, Instant at) {
        jdbcClient.sql("""
                        INSERT INTO support_agent_availability (agent_id, available, changed_at)
                        VALUES (:agentId, :available, :at)
                        ON CONFLICT (agent_id) DO UPDATE SET available = EXCLUDED.available, changed_at = EXCLUDED.changed_at""")
                .param("agentId", agentId)
                .param("available", available)
                .param("at", at.atOffset(ZoneOffset.UTC))
                .update();
    }
}
