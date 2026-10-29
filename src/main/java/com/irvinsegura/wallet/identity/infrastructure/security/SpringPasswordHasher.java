package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class SpringPasswordHasher implements PasswordHasher {

    private static final String BCRYPT = "bcrypt";

    private final PasswordEncoder encoder;

    SpringPasswordHasher(IdentityProperties properties) {
        // Hashes are stored with an {bcrypt} prefix, so the algorithm can be changed later without breaking old ones
        this.encoder = new DelegatingPasswordEncoder(
                BCRYPT, Map.of(BCRYPT, new BCryptPasswordEncoder(properties.password().hashStrength())));
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
