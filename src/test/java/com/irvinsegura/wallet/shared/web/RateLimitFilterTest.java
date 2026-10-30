package com.irvinsegura.wallet.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

class RateLimitFilterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final List<RateLimitExceededException> rejections = new ArrayList<>();
    private final HandlerExceptionResolver exceptionResolver = (request, response, handler, ex) -> {
        rejections.add((RateLimitExceededException) ex);
        response.setStatus(429);
        return new ModelAndView();
    };

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsOnceTheBucketIsEmptyAndSaysWhenToRetry() throws Exception {
        RateLimitFilter filter = filter(rule("auth", List.of("POST"), "/api/v1/auth/**",
                RateLimitProperties.ClientKey.IP, 2, Duration.ofMinutes(1)));

        assertThat(send(filter, "POST", "/api/v1/auth/login", "10.0.0.1").remaining()).isEqualTo("1");
        assertThat(send(filter, "POST", "/api/v1/auth/login", "10.0.0.1").remaining()).isEqualTo("0");
        Result third = send(filter, "POST", "/api/v1/auth/login", "10.0.0.1");

        assertThat(third.passed()).isFalse();
        // Two per minute refill one token every 30 seconds
        assertThat(rejections).singleElement()
                .satisfies(ex -> assertThat(ex.retryAfterSeconds()).isBetween(1L, 30L));
        assertThat(meterRegistry.get("wallet.rate.limit.rejections").tag("rule", "auth").counter().count())
                .isEqualTo(1);
    }

    @Test
    void eachAddressHasItsOwnBucket() throws Exception {
        RateLimitFilter filter = filter(rule("auth", List.of(), "/api/v1/auth/**",
                RateLimitProperties.ClientKey.IP, 1, Duration.ofMinutes(1)));

        assertThat(send(filter, "POST", "/api/v1/auth/login", "10.0.0.1").passed()).isTrue();
        assertThat(send(filter, "POST", "/api/v1/auth/login", "10.0.0.1").passed()).isFalse();
        assertThat(send(filter, "POST", "/api/v1/auth/login", "10.0.0.2").passed()).isTrue();
    }

    @Test
    void authenticatedUsersAreLimitedByIdWhateverTheirAddress() throws Exception {
        RateLimitFilter filter = filter(rule("api", List.of(), "/api/**",
                RateLimitProperties.ClientKey.USER, 1, Duration.ofMinutes(1)));

        authenticateAs("user-1");
        assertThat(send(filter, "GET", "/api/v1/wallets", "10.0.0.1").passed()).isTrue();
        assertThat(send(filter, "GET", "/api/v1/wallets", "10.0.0.2").passed()).isFalse();
        // Another user behind the same address (e.g. a shared office network) is not affected
        authenticateAs("user-2");
        assertThat(send(filter, "GET", "/api/v1/wallets", "10.0.0.1").passed()).isTrue();
    }

    @Test
    void firstMatchingRuleWinsAndUnmatchedRequestsAreNotLimited() throws Exception {
        RateLimitFilter filter = filter(
                rule("money", List.of("POST"), "/api/v1/wallets/*/deposits", RateLimitProperties.ClientKey.IP, 1,
                        Duration.ofMinutes(1)),
                rule("api", List.of(), "/api/**", RateLimitProperties.ClientKey.IP, 100, Duration.ofMinutes(1)));

        assertThat(send(filter, "POST", "/api/v1/wallets/abc/deposits", "10.0.0.1").passed()).isTrue();
        assertThat(send(filter, "POST", "/api/v1/wallets/abc/deposits", "10.0.0.1").passed()).isFalse();
        // Same path with another method falls through to the broad rule
        assertThat(send(filter, "GET", "/api/v1/wallets/abc/deposits", "10.0.0.1").remaining()).isEqualTo("99");
        Result actuator = send(filter, "GET", "/actuator/health", "10.0.0.1");
        assertThat(actuator.passed()).isTrue();
        assertThat(actuator.remaining()).isNull();
    }

    private RateLimitFilter filter(RateLimitProperties.Rule... rules) {
        return new RateLimitFilter(new RateLimitProperties(true, List.of(rules)), exceptionResolver, meterRegistry);
    }

    private static RateLimitProperties.Rule rule(String name, List<String> methods, String path,
            RateLimitProperties.ClientKey key, long capacity, Duration period) {
        return new RateLimitProperties.Rule(name, methods, List.of(path), key, capacity, period);
    }

    private static void authenticateAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null, "ROLE_USER"));
    }

    private static Result send(RateLimitFilter filter, String method, String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(chain.getRequest() != null, response.getHeader(RateLimitFilter.REMAINING_HEADER));
    }

    private record Result(boolean passed, String remaining) {
    }
}
