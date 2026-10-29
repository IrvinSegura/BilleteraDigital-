package com.irvinsegura.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(WalletApplication.class);

    @Test
    void modulesOnlyUseEachOthersPublicApi() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // Generates PlantUML diagrams and module canvases under target/spring-modulith-docs
        new Documenter(modules).writeDocumentation();
    }
}
