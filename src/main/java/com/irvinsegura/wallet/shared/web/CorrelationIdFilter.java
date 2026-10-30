package com.irvinsegura.wallet.shared.web;

import io.micrometer.common.KeyValue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Tags every request with a correlation id so all log lines and error responses of one request can be linked. The id
 * is also added to the request's trace, so a support ticket quoting it leads straight to the trace.
 */
@Component
// Right after Spring's observation filter (HIGHEST_PRECEDENCE + 1), which must have started the request's
// observation before the id can be attached to it
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String TRACE_TAG = "correlation.id";

    // Client-supplied ids end up in logs, so only short, safe values are accepted to prevent log injection
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        // High cardinality: goes on the span only, never on metrics
        ServerHttpObservationFilter.findObservationContext(request).ifPresent(
                context -> context.addHighCardinalityKeyValue(KeyValue.of(TRACE_TAG, correlationId)));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolveCorrelationId(String headerValue) {
        if (headerValue != null && VALID_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }
}
