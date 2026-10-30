package com.irvinsegura.wallet.shared.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Module event listeners run asynchronously after the publishing transaction commits, so a slow or failing side
 * effect (audit, notifications) never delays or rolls back the operation that caused it.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {
}
