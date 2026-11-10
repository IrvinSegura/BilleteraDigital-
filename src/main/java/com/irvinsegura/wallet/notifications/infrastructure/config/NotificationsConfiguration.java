package com.irvinsegura.wallet.notifications.infrastructure.config;

import com.irvinsegura.wallet.notifications.domain.model.AccountEmails;
import com.irvinsegura.wallet.notifications.domain.model.NotificationEmails;
import com.irvinsegura.wallet.notifications.domain.port.EmailSender;
import com.irvinsegura.wallet.notifications.infrastructure.email.LoggingEmailSender;
import com.irvinsegura.wallet.notifications.infrastructure.email.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationsProperties.class)
class NotificationsConfiguration {

    private static final String DELIVERY = "wallet.notifications.email.delivery";

    // Startup fails without a mail server instead of every email failing later
    @Bean
    @ConditionalOnProperty(name = DELIVERY, havingValue = "smtp")
    EmailSender smtpEmailSender(ObjectProvider<JavaMailSender> mailSender, NotificationsProperties properties,
            @Value("${spring.mail.host:}") String mailHost) {
        if (mailHost.isBlank()) {
            throw new IllegalStateException("Email delivery is smtp but spring.mail.host (MAIL_HOST) is not set");
        }
        return new SmtpEmailSender(mailSender.getObject(), properties.email().from());
    }

    @Bean
    @ConditionalOnProperty(name = DELIVERY, havingValue = "log")
    EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }

    @Bean
    AccountEmails accountEmails(NotificationsProperties properties) {
        return new AccountEmails(properties.email().appUrl(), properties.email().timeZone());
    }

    @Bean
    NotificationEmails notificationEmails(NotificationsProperties properties) {
        return new NotificationEmails(properties.email().appUrl(), properties.email().timeZone());
    }
}
