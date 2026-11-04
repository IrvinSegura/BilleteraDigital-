package com.irvinsegura.wallet.identity.domain.port;

/**
 * Tells people from bots on the requests that send email to any address (sign-up, password reset), so they cannot be
 * scripted to flood someone's inbox or to probe many addresses.
 */
public interface HumanVerifier {

    /**
     * @param token    what the CAPTCHA widget gave the client; may be {@code null}
     * @param remoteIp the client's address, which the provider checks the token against; may be {@code null}
     */
    boolean isHuman(String token, String remoteIp);

    /** For when the check is turned off: everyone passes. */
    static HumanVerifier none() {
        return (token, remoteIp) -> true;
    }
}
