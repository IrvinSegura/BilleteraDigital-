package com.irvinsegura.wallet.shared.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs every module's background jobs (reconciliation, cleanups). Turned off in tests, which call the jobs'
 * services directly so they never race with a job firing in the middle of a test.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "wallet.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfiguration {
}
