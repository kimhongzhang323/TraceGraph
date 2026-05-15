package io.tracegraph.core.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.tracegraph.core", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreArchitectureTest {

    @ArchTest
    static final ArchRule noSpringDependency =
            noClasses()
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .as("tracegraph-core must not depend on Spring");

    @ArchTest
    static final ArchRule noJacksonDependency =
            noClasses()
                    .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
                    .as("tracegraph-core must not depend on Jackson");

    @ArchTest
    static final ArchRule noOtelDependency =
            noClasses()
                    .should().dependOnClassesThat().resideInAPackage("io.opentelemetry..")
                    .as("tracegraph-core must not depend on OpenTelemetry");

    @ArchTest
    static final ArchRule spiInterfacesAreInSpiPackage =
            classes()
                    .that().resideInAPackage("io.tracegraph.core.spi")
                    .and().areNotAnonymousClasses()
                    .should().bePublic()
                    .as("Named types in *.spi must be public interfaces or records");

    @ArchTest
    static final ArchRule exceptionsInExecHaveExceptionSuffix =
            classes()
                    .that().resideInAPackage("io.tracegraph.core.exec")
                    .and().areAssignableTo(RuntimeException.class)
                    .should().haveSimpleNameEndingWith("Exception")
                    .as("Exception types in exec must end with 'Exception'");

    @ArchTest
    static final ArchRule noSystemOutInProductionCode =
            noClasses()
                    .should().accessField(System.class, "out")
                    .orShould().accessField(System.class, "err")
                    .as("Production code must not write to System.out or System.err; use SLF4J");
}
