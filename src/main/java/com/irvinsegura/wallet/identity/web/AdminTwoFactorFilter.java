package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.identity.domain.model.AdminTwoFactorRequiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Administrators can freeze wallets, disable accounts, reset other users' second factor and record capital, so a
 * stolen administrator password alone must not be enough: {@code /api/v1/admin/**} needs a session of an
 * administrator with two-factor authentication on. They can still sign in and set it up through their own profile.
 */
class AdminTwoFactorFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH = "/api/v1/admin/";

    private final HandlerExceptionResolver exceptionResolver;

    AdminTwoFactorFilter(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(request.getContextPath() + ADMIN_PATH) && !hasTwoFactor()) {
            // Same Problem Details body as any other error
            exceptionResolver.resolveException(request, response, null, new AdminTwoFactorRequiredException());
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean hasTwoFactor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(AccessToken.TWO_FACTOR_CLAIM));
    }
}
