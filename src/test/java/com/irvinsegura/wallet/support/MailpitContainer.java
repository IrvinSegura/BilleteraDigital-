package com.irvinsegura.wallet.support;

import org.testcontainers.containers.GenericContainer;

/**
 * Mailpit, a fake SMTP server whose HTTP API lists the emails it received. Its own type keeps it apart from the
 * PostgreSQL container, which is also a {@link GenericContainer}, when beans are injected by type.
 */
public class MailpitContainer extends GenericContainer<MailpitContainer> {

    private static final int SMTP_PORT = 1025;
    private static final int HTTP_PORT = 8025;

    public MailpitContainer() {
        super("axllent/mailpit:v1.31.6");
        withExposedPorts(SMTP_PORT, HTTP_PORT);
    }

    public int smtpPort() {
        return getMappedPort(SMTP_PORT);
    }

    public String apiUrl() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT);
    }
}
