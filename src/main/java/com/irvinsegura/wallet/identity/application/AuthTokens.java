package com.irvinsegura.wallet.identity.application;

import java.time.Duration;

public record AuthTokens(
        String accessToken,
        Duration accessTokenExpiresIn,
        String refreshToken,
        Duration refreshTokenExpiresIn) {
}
