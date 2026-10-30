package com.irvinsegura.wallet.shared.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Token-bucket rate limiting per client and rule. Registered right after the security filter chain, so the
 * authenticated user is known and requests the chain already rejected (e.g. without a token) cost nothing here.
 * <p>
 * Buckets live in memory, so with several instances each one enforces the limits on its own share of traffic.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    // Bounds memory when many clients (or spoofed IPv6 addresses) show up; evicting an idle bucket loses nothing,
    // because it would have refilled completely anyway
    private static final long MAX_TRACKED_CLIENTS = 100_000;

    private final List<CompiledRule> rules;
    private final Cache<String, Bucket> buckets;
    private final HandlerExceptionResolver exceptionResolver;
    private final Map<String, Counter> rejections;

    public RateLimitFilter(RateLimitProperties properties, HandlerExceptionResolver exceptionResolver,
            MeterRegistry meterRegistry) {
        this.rules = properties.rules().stream().map(CompiledRule::new).toList();
        Duration longestPeriod = properties.rules().stream()
                .map(RateLimitProperties.Rule::period)
                .max(Comparator.naturalOrder())
                .orElse(Duration.ofMinutes(1));
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_CLIENTS)
                .expireAfterAccess(longestPeriod)
                .build();
        this.exceptionResolver = exceptionResolver;
        this.rejections = properties.rules().stream().collect(Collectors.toMap(RateLimitProperties.Rule::name,
                rule -> Counter.builder("wallet.rate.limit.rejections")
                        .description("Requests rejected with 429 by the rate limiter")
                        .tag("rule", rule.name())
                        .register(meterRegistry),
                (first, second) -> first));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CompiledRule rule = findRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        Bucket bucket = buckets.get(rule.name() + "|" + clientKey(rule, request), key -> rule.newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader(REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        rejections.get(rule.name()).increment();
        response.setHeader(REMAINING_HEADER, "0");
        // Same Problem Details body as any other error, written by the global exception handler
        exceptionResolver.resolveException(request, response, null,
                new RateLimitExceededException(Duration.ofNanos(probe.getNanosToWaitForRefill())));
    }

    private CompiledRule findRule(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(
                request.getRequestURI().substring(request.getContextPath().length()));
        for (CompiledRule rule : rules) {
            if (rule.matches(request.getMethod(), path)) {
                return rule;
            }
        }
        return null;
    }

    private static String clientKey(CompiledRule rule, HttpServletRequest request) {
        if (rule.key() == RateLimitProperties.ClientKey.USER) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)) {
                return "user:" + authentication.getName();
            }
        }
        // Behind a proxy this is only the client's address when server.forward-headers-strategy is configured;
        // X-Forwarded-For is never read directly because clients can forge it
        return "ip:" + request.getRemoteAddr();
    }

    private record CompiledRule(String name, List<String> methods, List<PathPattern> paths,
            RateLimitProperties.ClientKey key, long capacity, Duration period) {

        CompiledRule(RateLimitProperties.Rule rule) {
            this(rule.name(), rule.methods().stream().map(String::toUpperCase).toList(),
                    rule.paths().stream().map(PathPatternParser.defaultInstance::parse).toList(),
                    rule.key(), rule.capacity(), rule.period());
        }

        boolean matches(String method, PathContainer path) {
            return (methods.isEmpty() || methods.contains(method))
                    && paths.stream().anyMatch(pattern -> pattern.matches(path));
        }

        // Greedy refill adds tokens continuously instead of all at once, so a client cannot burst twice the
        // capacity across a period boundary
        Bucket newBucket() {
            return Bucket.builder()
                    .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                    .build();
        }
    }
}
