package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.identity.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public interface AccessTokenIssuer {

    /**
     * @param twoFactorEnabled whether the user has two-factor authentication on
     * @param sessionId        the refresh token family the access token belongs to
     */
    AccessToken issue(User user, boolean twoFactorEnabled, UUID sessionId, Instant now);
}
