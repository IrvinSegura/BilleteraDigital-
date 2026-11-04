package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.AuthTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Browser sessions: a web app asks for them with {@code X-Session-Mode: cookie}, and the refresh token then travels
 * only in an {@code HttpOnly} cookie that page scripts cannot read, so an XSS bug cannot steal a long-lived session.
 * <p>
 * The cookie is only accepted together with that header. Browsers send cookies on their own, but a page on another
 * site cannot add a custom header without passing CORS, so a forged cross-site request cannot use the cookie.
 */
@Component
class RefreshTokenCookies {

    static final String SESSION_MODE_HEADER = "X-Session-Mode";
    private static final String COOKIE_MODE = "cookie";
    // Sent only to the token endpoints, never with the rest of the API
    private static final String PATH = "/api/v1/auth";

    private final String name;
    private final boolean secure;
    private final String sameSite;

    RefreshTokenCookies(@Value("${wallet.identity.refresh-token-cookie.name}") String name,
            @Value("${wallet.identity.refresh-token-cookie.secure}") boolean secure,
            @Value("${wallet.identity.refresh-token-cookie.same-site}") String sameSite) {
        this.name = name;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    static boolean wantsCookie(HttpServletRequest request) {
        return COOKIE_MODE.equalsIgnoreCase(request.getHeader(SESSION_MODE_HEADER));
    }

    /**
     * The token pair as the client asked for it: in the body, or with the refresh token in the cookie only.
     */
    ResponseEntity<AuthController.TokenResponse> respond(HttpServletRequest request, AuthTokens tokens) {
        if (!wantsCookie(request)) {
            return ResponseEntity.ok(AuthController.TokenResponse.from(tokens));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), tokens.refreshTokenExpiresIn()))
                .body(AuthController.TokenResponse.withoutRefreshToken(tokens));
    }

    /**
     * The refresh token from the body, or from the cookie of a browser session.
     */
    Optional<String> read(HttpServletRequest request, String fromBody) {
        if (fromBody != null && !fromBody.isBlank()) {
            return Optional.of(fromBody);
        }
        if (!wantsCookie(request) || request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    String cleared() {
        return cookie("", Duration.ZERO);
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
