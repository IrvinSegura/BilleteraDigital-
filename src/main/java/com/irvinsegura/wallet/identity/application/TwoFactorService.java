package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.InvalidTwoFactorCodeException;
import com.irvinsegura.wallet.identity.TwoFactorDisabled;
import com.irvinsegura.wallet.identity.TwoFactorEnabled;
import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.IncorrectPasswordException;
import com.irvinsegura.wallet.identity.domain.model.RecoveryCodes;
import com.irvinsegura.wallet.identity.domain.model.Totp;
import com.irvinsegura.wallet.identity.domain.model.TwoFactor;
import com.irvinsegura.wallet.identity.domain.model.TwoFactorAlreadyEnabledException;
import com.irvinsegura.wallet.identity.domain.model.TwoFactorNotEnabledException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.SecretCipher;
import com.irvinsegura.wallet.identity.domain.port.TwoFactorRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turning two-factor authentication on and off. Setting it up needs the password; turning it off needs the password
 * and a second factor, so neither a stolen access token nor a stolen password alone can remove it.
 */
@Service
public class TwoFactorService {

    private final UserRepository userRepository;
    private final TwoFactorRepository twoFactorRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordVerifier passwordVerifier;
    private final SecondFactorVerifier secondFactorVerifier;
    private final SecretCipher cipher;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String issuer;

    TwoFactorService(UserRepository userRepository, TwoFactorRepository twoFactorRepository,
            RefreshTokenRepository refreshTokenRepository, PasswordVerifier passwordVerifier, SecondFactorVerifier secondFactorVerifier, SecretCipher cipher,
            ApplicationEventPublisher events, Clock clock,
            @Value("${wallet.identity.two-factor.issuer}") String issuer) {
        this.userRepository = userRepository;
        this.twoFactorRepository = twoFactorRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordVerifier = passwordVerifier;
        this.secondFactorVerifier = secondFactorVerifier;
        this.cipher = cipher;
        this.events = events;
        this.clock = clock;
        this.issuer = issuer;
    }

    /**
     * Starts setting up an authenticator app; nothing changes at login until {@link #enable} confirms it. Starting
     * again replaces an unconfirmed setup.
     *
     * @throws IncorrectPasswordException       if the password is wrong; it counts as a failed login
     * @throws TwoFactorAlreadyEnabledException if an app is already set up
     */
    @Transactional(noRollbackFor = IncorrectPasswordException.class)
    public TwoFactorSetup begin(UUID userId, String currentPassword) {
        Instant now = clock.instant();
        User user = verifiedUser(userId, currentPassword, now);
        if (twoFactorRepository.findByUserIdForUpdate(user.id()).filter(TwoFactor::isEnabled).isPresent()) {
            throw new TwoFactorAlreadyEnabledException();
        }
        String secret = Totp.newSecret();
        twoFactorRepository.delete(user.id());
        twoFactorRepository.save(TwoFactor.start(user.id(), cipher.encrypt(secret), now));
        return new TwoFactorSetup(secret, otpauthUri(user, secret));
    }

    /**
     * Confirms the app with a code it shows and turns two-factor authentication on. Returns the recovery codes, which
     * are never shown again. Every session ends, the caller's too: a session someone stole before keeps no access,
     * and the next login proves the second factor.
     *
     * @throws TwoFactorNotEnabledException   if {@link #begin} was not called
     * @throws InvalidTwoFactorCodeException  if the code is wrong
     */
    @Transactional
    public List<String> enable(UUID userId, String code) {
        UserId id = new UserId(userId);
        Instant now = clock.instant();
        TwoFactor twoFactor = twoFactorRepository.findByUserIdForUpdate(id)
                .orElseThrow(TwoFactorNotEnabledException::new);
        if (twoFactor.isEnabled()) {
            throw new TwoFactorAlreadyEnabledException();
        }
        if (!twoFactor.acceptCode(cipher.decrypt(twoFactor.encryptedSecret()), code, now)) {
            throw new InvalidTwoFactorCodeException();
        }
        twoFactor.enable(now);
        twoFactorRepository.save(twoFactor);
        List<String> recoveryCodes = RecoveryCodes.generate();
        twoFactorRepository.replaceRecoveryCodes(id, recoveryCodes.stream().map(RecoveryCodes::hash).toList(), now);
        refreshTokenRepository.revokeAllForUser(id, now);
        events.publishEvent(new TwoFactorEnabled(userId, now));
        return recoveryCodes;
    }

    /**
     * @param code the app's code or a recovery code
     * @throws IncorrectPasswordException    if the password is wrong; it counts as a failed login
     * @throws InvalidTwoFactorCodeException if the code is wrong; it counts as a failed login
     */
    @Transactional(noRollbackFor = {IncorrectPasswordException.class, InvalidTwoFactorCodeException.class})
    public void disable(UUID userId, String currentPassword, String code) {
        Instant now = clock.instant();
        User user = verifiedUser(userId, currentPassword, now);
        if (!secondFactorVerifier.isEnabled(user.id())) {
            throw new TwoFactorNotEnabledException();
        }
        if (!secondFactorVerifier.verify(user.id(), code, now)) {
            passwordVerifier.recordFailedAttempt(user, now);
            throw new InvalidTwoFactorCodeException();
        }
        twoFactorRepository.delete(user.id());
        events.publishEvent(new TwoFactorDisabled(userId, userId, null, now));
    }

    /**
     * For a user who lost their phone and their recovery codes, once support has confirmed who they are. Returns
     * whether there was anything to turn off.
     */
    @Transactional
    public boolean reset(UUID administratorId, UUID userId, String reason) {
        User user = userRepository.findByIdForUpdate(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        if (!secondFactorVerifier.isEnabled(user.id())) {
            return false;
        }
        twoFactorRepository.delete(user.id());
        events.publishEvent(new TwoFactorDisabled(userId, administratorId, reason, clock.instant()));
        return true;
    }

    @Transactional(readOnly = true)
    public int unusedRecoveryCodes(UUID userId) {
        return twoFactorRepository.countUnusedRecoveryCodes(new UserId(userId));
    }

    private User verifiedUser(UUID userId, String currentPassword, Instant now) {
        User user = userRepository.findByIdForUpdate(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        if (user.isLockedAt(now)) {
            throw new AccountLockedException(user.lockedUntil());
        }
        if (!passwordVerifier.verify(user, currentPassword, now)) {
            throw new IncorrectPasswordException();
        }
        return user;
    }

    // Key URI format of Google Authenticator, which every authenticator app reads
    private String otpauthUri(User user, String secret) {
        // A literal colon between issuer and account: some apps do not recognise it encoded
        String label = encode(issuer) + ":" + encode(user.email().value());
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=%d".formatted(label, secret,
                encode(issuer), Totp.STEP.toSeconds());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
