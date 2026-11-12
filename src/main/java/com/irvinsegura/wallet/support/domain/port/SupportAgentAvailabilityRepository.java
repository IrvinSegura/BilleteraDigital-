package com.irvinsegura.wallet.support.domain.port;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Whether each agent takes new cases. Every agent does until they pause.
 */
public interface SupportAgentAvailabilityRepository {

    /**
     * Those of the agents who paused.
     */
    Set<UUID> findPaused(Collection<UUID> agents);

    void save(UUID agentId, boolean available, Instant at);
}
