package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.AccessTokenIssuer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final IdentityProperties.Jwt properties;

    JwtAccessTokenIssuer(JwtEncoder jwtEncoder, IdentityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties.jwt();
    }

    @Override
    public AccessToken issue(User user, boolean twoFactorEnabled, UUID sessionId, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        // Only the user id and roles: tokens are readable by anyone holding them, so no personal data goes in
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(AccessToken.ROLES_CLAIM, List.of(user.role().name()))
                .claim(AccessToken.TWO_FACTOR_CLAIM, twoFactorEnabled)
                .claim(AccessToken.PASSWORD_CHANGE_CLAIM, user.mustChangePassword())
                .claim(AccessToken.SESSION_CLAIM, sessionId.toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, now, expiresAt);
    }
}
