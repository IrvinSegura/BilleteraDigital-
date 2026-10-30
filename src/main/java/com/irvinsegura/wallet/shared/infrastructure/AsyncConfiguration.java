package com.irvinsegura.wallet.shared.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Module event listeners run asynchronously after the publishing transaction commits, so a slow or failing side
 * effect (audit, notifications) never delays or rolls back the operation that caused it.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {

    // Boot applies it to the application task executor: listeners continue the trace of the request that
    // published the event instead of starting unrelated traces
    @Bean
    TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
