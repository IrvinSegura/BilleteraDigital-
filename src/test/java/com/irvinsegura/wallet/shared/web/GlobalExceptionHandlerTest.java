package com.irvinsegura.wallet.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.shared.domain.DomainException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FailingController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class, GlobalExceptionHandlerTest.FailingController.class,
        GlobalExceptionHandlerTest.Metrics.class})
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void domainExceptionBecomes422WithItsCode() throws Exception {
        mockMvc.perform(get("/test/domain-error"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.detail").value("Not enough balance"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        assertThat(meterRegistry.get("wallet.business.rejections").tag("code", "INSUFFICIENT_FUNDS").counter()
                .count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void rateLimitBecomes429WithRetryAfterInWholeSeconds() throws Exception {
        mockMvc.perform(get("/test/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void validationErrorsListEveryInvalidField() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("amount"));
    }

    @Test
    void unexpectedErrorsHideInternalDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void accessDeniedBecomes403() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidCorrelationIdIsReplaced() throws Exception {
        mockMvc.perform(get("/test/domain-error").header(CorrelationIdFilter.HEADER, "bad value\nINJECTED"))
                .andExpect(jsonPath("$.correlationId").value(not("bad value\nINJECTED")));
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/domain-error")
        void domainError() {
            throw new DomainException("INSUFFICIENT_FUNDS", "Not enough balance") {
            };
        }

        @GetMapping("/test/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("database password is hunter2");
        }

        @GetMapping("/test/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("missing role");
        }

        @GetMapping("/test/rate-limited")
        void rateLimited() {
            throw new RateLimitExceededException(Duration.ofMillis(1500));
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody AmountRequest request) {
        }
    }

    record AmountRequest(@Positive long amount) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Metrics {

        @Bean
        @Primary
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
