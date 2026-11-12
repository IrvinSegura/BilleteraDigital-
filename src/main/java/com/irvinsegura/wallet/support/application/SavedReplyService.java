package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.support.domain.model.SavedReply;
import com.irvinsegura.wallet.support.domain.model.SavedReplyLimitException;
import com.irvinsegura.wallet.support.domain.model.SavedReplyNotFoundException;
import com.irvinsegura.wallet.support.domain.port.SavedReplyRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The answers each agent keeps to send again. They are the agent's own: no one else sees or changes them.
 */
@Service
public class SavedReplyService {

    private final SavedReplyRepository replies;
    private final Clock clock;

    SavedReplyService(SavedReplyRepository replies, Clock clock) {
        this.replies = replies;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SavedReply> list(UUID agentId) {
        return replies.findByAgent(agentId);
    }

    /**
     * @throws SavedReplyLimitException if the agent already keeps as many as allowed
     */
    @Transactional
    public SavedReply save(UUID agentId, String title, String text) {
        SavedReply reply = SavedReply.create(agentId, title, text, clock.instant());
        if (!replies.insert(reply)) {
            throw new SavedReplyLimitException();
        }
        return reply;
    }

    /**
     * @throws SavedReplyNotFoundException if the agent has no such reply
     */
    @Transactional
    public void delete(UUID agentId, UUID replyId) {
        if (!replies.delete(agentId, replyId)) {
            throw new SavedReplyNotFoundException();
        }
    }
}
