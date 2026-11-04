package com.irvinsegura.wallet.identity.domain.model;

/**
 * The browser or app and the address a request came from, as the user's session list shows them. Either may be
 * {@code null} when unknown.
 */
public record ClientInfo(String userAgent, String ipAddress) {

    public static final int MAX_USER_AGENT_LENGTH = 255;

    public ClientInfo {
        if (userAgent != null) {
            userAgent = userAgent.strip();
            if (userAgent.isEmpty()) {
                userAgent = null;
            } else if (userAgent.length() > MAX_USER_AGENT_LENGTH) {
                userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
            }
        }
    }

    public static ClientInfo unknown() {
        return new ClientInfo(null, null);
    }
}
