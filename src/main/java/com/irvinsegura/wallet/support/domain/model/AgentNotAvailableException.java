package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The colleague a case was passed to is not an active agent free to take it.
 */
public class AgentNotAvailableException extends DomainException {

    public AgentNotAvailableException() {
        super("SUPPORT_AGENT_NOT_AVAILABLE", "That agent can't take the case right now");
    }
}
