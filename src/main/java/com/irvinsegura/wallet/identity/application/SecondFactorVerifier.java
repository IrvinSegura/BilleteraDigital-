package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.TwoFactorRequiredException;
import com.irvinsegura.wallet.identity.domain.model.RecoveryCodes;
import com.irvinsegura.wallet.identity.domain.model.TwoFactor;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.SecretCipher;
import com.irvinsegura.wallet.identity.domain.port.TwoFactorRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Checks the second factor of a user who has one, in the caller's transaction: a code from the authenticator app or
 * one of the recovery codes. Login and step-up confirmations share it; their callers count failures toward the
 * lockout, which is what makes guessing six digits hopeless.
 */
@Component
class SecondFactorVerifier {

    private final TwoFactorRepository repository;
    private final SecretCipher cipher;

    SecondFactorVerifier(TwoFactorRepository repository, SecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    boolean isEnabled(UserId userId) {
        return repository.findByUserId(userId).filter(TwoFactor::isEnabled).isPresent();
    }

    /**
     * Returns true when the user has no second factor, or the code is valid and now used up.
     *
     * @throws TwoFactorRequiredException if the user has one and no code was given
     */
    boolean verify(UserId userId, String code, Instant now) {
        Optional<TwoFactor> found = repository.findByUserIdForUpdate(userId).filter(TwoFactor::isEnabled);
        if (found.isEmpty()) {
            return true;
        }
        if (code == null || code.isBlank()) {
            throw new TwoFactorRequiredException();
        }
        if (RecoveryCodes.looksLikeRecoveryCode(code)) {
            return repository.useRecoveryCode(userId, RecoveryCodes.hash(code), now);
        }
        TwoFactor twoFactor = found.get();
        if (!twoFactor.acceptCode(cipher.decrypt(twoFactor.encryptedSecret()), code.strip(), now)) {
            return false;
        }
        repository.save(twoFactor);
        return true;
    }
}
