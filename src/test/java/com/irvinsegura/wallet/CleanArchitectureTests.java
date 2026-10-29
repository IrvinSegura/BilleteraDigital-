package com.irvinsegura.wallet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the dependency rule inside every module: web -> application -> domain <- infrastructure.
 * Rules allow empty packages so they keep passing while modules are still being built.
 */
@AnalyzeClasses(packagesOf = WalletApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTests {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..",
                    "com.fasterxml.jackson..", "jakarta.servlet..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..web..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..web..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule webDoesNotUseInfrastructure = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule infrastructureDoesNotUseWeb = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat()
            .resideInAPackage("..web..")
            .allowEmptyShould(true);
}
