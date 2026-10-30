package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.application.AdminProvisioningService;
import com.irvinsegura.wallet.identity.domain.model.EmailAlreadyRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AdminProvisioningService adminProvisioningService;
    private final IdentityProperties.Admin admin;

    AdminAccountInitializer(AdminProvisioningService adminProvisioningService, IdentityProperties properties) {
        this.adminProvisioningService = adminProvisioningService;
        this.admin = properties.admin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (admin == null || !admin.isConfigured()) {
            log.info("No administrator configured (wallet.identity.admin.email / password)");
            return;
        }
        try {
            adminProvisioningService.ensureAdmin(admin.email(), admin.password());
        } catch (EmailAlreadyRegisteredException e) {
            // Another instance starting at the same time created it first
            log.info("Administrator account already created by another instance");
        }
    }
}
