package com.irvinsegura.wallet.support.domain.port;

import com.irvinsegura.wallet.support.domain.model.SavedReply;
import java.util.List;
import java.util.UUID;

public interface SavedReplyRepository {

    /**
     * Adds it unless the agent already keeps {@link SavedReply#MAX_PER_AGENT}; serialized per agent.
     *
     * @return whether it was added
     */
    boolean insert(SavedReply reply);

    /**
     * The agent's saved replies, by title.
     */
    List<SavedReply> findByAgent(UUID agentId);

    /**
     * @return whether the agent had it
     */
    boolean delete(UUID agentId, UUID replyId);
}
