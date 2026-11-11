package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.identity.domain.model.PasswordChangeRequiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * A session of an account that still has its emailed password can only replace it: that password also sits in an
 * inbox, so on its own it must not reach anything. The owner can still see who they are, keep their language and leave.
 */
class PasswordChangeFilter extends OncePerRequestFilter {

    private static final String API_PATH = "/api/v1/";
    private static final String AUTH_PATH = "/api/v1/auth/";
    private static final Set<String> ALLOWED = Set.of(
            "GET /api/v1/users/me", "PUT /api/v1/users/me/language", "POST /api/v1/users/me/password");

    private final HandlerExceptionResolver exceptionResolver;

    PasswordChangeFilter(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith(API_PATH) && !path.startsWith(AUTH_PATH)
                && !ALLOWED.contains(request.getMethod() + " " + path) && mustChangePassword()) {
            // Same Problem Details body as any other error
            exceptionResolver.resolveException(request, response, null, new PasswordChangeRequiredException());
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean mustChangePassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(AccessToken.PASSWORD_CHANGE_CLAIM));
    }
}
