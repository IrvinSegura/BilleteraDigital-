package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.HumanVerificationFailedException;
import com.irvinsegura.wallet.identity.domain.port.ClientInfoSource;
import com.irvinsegura.wallet.identity.domain.port.HumanVerifier;
import org.springframework.stereotype.Service;

/**
 * The CAPTCHA check in front of sign-up and password reset requests. It runs before their transactions, since it
 * waits on the provider.
 */
@Service
public class HumanCheck {

    private final HumanVerifier verifier;
    private final ClientInfoSource clientInfo;

    HumanCheck(HumanVerifier verifier, ClientInfoSource clientInfo) {
        this.verifier = verifier;
        this.clientInfo = clientInfo;
    }

    /**
     * @throws HumanVerificationFailedException if the token does not prove a person sent the request
     */
    public void require(String captchaToken) {
        if (!verifier.isHuman(captchaToken, clientInfo.current().ipAddress())) {
            throw new HumanVerificationFailedException();
        }
    }
}
