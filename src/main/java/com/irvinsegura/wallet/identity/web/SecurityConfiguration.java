package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            Environment environment) throws Exception {
        AuthenticationEntryPoint entryPoint = problemDetailsEntryPoint(exceptionResolver);
        AccessDeniedHandler accessDeniedHandler = problemDetailsAccessDeniedHandler(exceptionResolver);
        RequestMatcher managementPort = managementPortMatcher(environment);
        http
                // No cookies or server sessions: tokens travel in the Authorization header, so CSRF does not apply
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // The management port is reachable only from inside the network (Prometheus, probes)
                        .requestMatchers(managementPort).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Without a separate management port, metrics and module details are for admins only
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Only served in the dev profile, see springdoc settings
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // One rule for every module's admin endpoints, so none can be exposed by mistake
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
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
