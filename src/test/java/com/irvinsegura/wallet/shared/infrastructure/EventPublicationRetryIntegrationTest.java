package com.irvinsegura.wallet.shared.infrastructure;

import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// No backoff, so a failure can be retried right away
@SpringBootTest(properties = "wallet.events.retry.initial-backoff=0s")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EventPublicationRetryIntegrationTest {

    @Autowired
    private EventPublicationRetrier retrier;

    @Autowired
    private FlakyListener listener;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aFailedListenerRunsAgainWithoutARestart() throws Exception {
        RetryProbe probe = new RetryProbe(UUID.randomUUID());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> events.publishEvent(probe));

        assertEventually(() -> assertThat(statusOf(probe)).isEqualTo("FAILED"));

        retrier.resubmitDueFailures();

        // Completed publications are deleted
        assertEventually(() -> assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?", Long.class,
                "%" + probe.id() + "%")).isZero());
        assertThat(listener.calls()).isEqualTo(2);
    }

    private String statusOf(RetryProbe probe) {
        return jdbcTemplate.queryForList("SELECT status FROM event_publication WHERE serialized_event LIKE ?",
                String.class, "%" + probe.id() + "%").stream().findFirst().orElse(null);
    }

    record RetryProbe(UUID id) {
    }

    // The bean is a proxy (async, transactional), so state is read through a method, never a field
    static class FlakyListener {

        private final AtomicInteger calls = new AtomicInteger();

        int calls() {
            return calls.get();
        }

        @ApplicationModuleListener
        void on(RetryProbe probe) {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("Fails the first time, like a mail server outage");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FlakyListenerConfiguration {

        @Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }
    }
}
