package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.identity.domain.model.User;
import java.time.Instant;

public interface AccessTokenIssuer {

    AccessToken issue(User user, Instant now);
}
