package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless API secured with bearer access tokens. Everything requires authentication unless listed here.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@SecurityScheme(name = SecurityConfiguration.BEARER_SCHEME, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT")
class SecurityConfiguration {

    // Referenced by controllers so Swagger UI sends the access token on protected endpoints
    static final String BEARER_SCHEME = "bearerAuth";

    // A trailing /** also matches the path itself
    private static final String[] CUSTOMER_ONLY = {"/api/v1/wallets/**", "/api/v1/transfers/**",
            "/api/v1/bank-accounts/**", "/api/v1/support/conversation/**", "/api/v1/users/me/identity-verification/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            Environment environment,
            @Value("${wallet.identity.admin-requires-two-factor}") boolean adminRequiresTwoFactor,
            @Value("${wallet.management.scrape-password:}") String scrapePassword) throws Exception {
        AuthenticationEntryPoint entryPoint = problemDetailsEntryPoint(exceptionResolver);
        AccessDeniedHandler accessDeniedHandler = problemDetailsAccessDeniedHandler(exceptionResolver);
        RequestMatcher managementPort = managementPortMatcher(environment);
        http
                // No server sessions: access tokens travel in the Authorization header, and the refresh token
                // cookie is only accepted with a custom header that a cross-site request cannot add
                // (see RefreshTokenCookies)
                .csrf(AbstractHttpConfigurer::disable)
                // Origins from wallet.web.cors.allowed-origins; preflight requests are answered before authentication
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/email-verification/confirm", "/api/v1/auth/password-reset",
                                "/api/v1/auth/password-reset/confirm", "/api/v1/auth/email-change/confirm").permitAll()
                        // Called by the payment provider, which proves itself with a signature instead of a token
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/**").permitAll()
                        // The management port is reachable only from inside the network: probes are open, and
                        // metrics and module details also need the scrape password where one is set
                        .requestMatchers(managementPort).access(scrapeAccess(scrapePassword))
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Without a separate management port, metrics and module details are for admins only
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Only served in the dev profile, see springdoc settings
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // One rule for every module's admin endpoints, so none can be exposed by mistake
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/support/agent/**").hasRole("SUPPORT")
                        // Money belongs to customers only: staff run the app, so an administrator or an agent can
                        // neither open a wallet, move money, add a bank account, open a customer chat nor verify an
                        // identity of their own; they see customers' activity through the admin endpoints
                        .requestMatchers(CUSTOMER_ONLY).hasRole("USER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        // After authorization, so only callers already allowed in are checked; first, because an account that still has
        // its emailed password has to replace it before it can turn on two-factor
        http.addFilterAfter(new PasswordChangeFilter(exceptionResolver), AuthorizationFilter.class);
        if (adminRequiresTwoFactor) {
            // After authorization, so only administrators already allowed in are checked
            http.addFilterAfter(new AdminTwoFactorFilter(exceptionResolver), AuthorizationFilter.class);
        }
        return http.build();
    }

    // Matches nothing when actuator shares the application port, so actuator rules below apply there
    private static RequestMatcher managementPortMatcher(Environment environment) {
        Integer managementPort = environment.getProperty("management.server.port", Integer.class);
        int serverPort = environment.getProperty("server.port", Integer.class, 8080);
        if (managementPort == null || managementPort <= 0 || managementPort == serverPort) {
            return request -> false;
        }
        return request -> request.getLocalPort() == managementPort;
    }

    // HTTP Basic as user "prometheus", which Prometheus sends with basic_auth; a bearer token would be read as a JWT
    private static AuthorizationManager<RequestAuthorizationContext> scrapeAccess(String password) {
        byte[] expected = ("prometheus:" + password).getBytes(StandardCharsets.UTF_8);
        return (authentication, context) -> {
            HttpServletRequest request = context.getRequest();
            String path = request.getRequestURI();
            if (password.isBlank() || path.startsWith("/actuator/health") || path.equals("/actuator/info")) {
                return new AuthorizationDecision(true);
            }
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
                return new AuthorizationDecision(false);
            }
            try {
                byte[] given = Base64.getDecoder().decode(header.substring(6).strip());
                return new AuthorizationDecision(MessageDigest.isEqual(given, expected));
            } catch (IllegalArgumentException e) {
                return new AuthorizationDecision(false);
            }
        };
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AccessToken.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    // The bearer handlers set the status and WWW-Authenticate header (RFC 6750); the exception resolver then writes
    // the same Problem Details body as any other error
    private static AuthenticationEntryPoint problemDetailsEntryPoint(HandlerExceptionResolver exceptionResolver) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, ex) -> {
            bearer.commence(request, response, ex);
            exceptionResolver.resolveException(request, response, null, ex);
        };
    }

    private static AccessDeniedHandler problemDetailsAccessDeniedHandler(HandlerExceptionResolver exceptionResolver) {
        BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (request, response, ex) -> {
            bearer.handle(request, response, ex);
            exceptionResolver.resolveException(request, response, null, ex);
        };
    }
}
