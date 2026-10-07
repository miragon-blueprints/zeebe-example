package io.miragon.common.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static io.miragon.common.architecture.condition.UseCaseDependencyConditions.ONLY_FULFIL_ONE_USE_CASE;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;
import io.miragon.common.architecture.condition.InterfaceImplementationConditions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * ArchUnit half of the combined suite: the <em>dependency &amp; structure</em> rules, checked against the
 * resolved bytecode graph. Naming conventions live in {@link NamingConventionArchitectureTest}.
 *
 * <p>The {@code adapter.process} package holds the <strong>generated</strong> BPMN process API and
 * process-engine configuration. It is a technical seam that does not fit the inbound/outbound split, so it
 * is excluded from the adapter-location rule and ignored by the layered-architecture completeness check.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class HexagonalArchitectureTest {

    /** Packages the domain layer is allowed to depend on — pure language, no infrastructure. */
    private static final String[] DOMAIN_ALLOWED_PACKAGES = {
        "..domain..",
        "java..",
        "", // allows the usage of primitive types
    };

    /** Packages the application layer is allowed to depend on — domain, ports and orchestration tooling. */
    private static final String[] APPLICATION_ALLOWED_PACKAGES = {
        "..domain..",
        "..application..",
        "java..",
        "org.slf4j..",
        "org.springframework..",
        "com.fasterxml.jackson..",
        "", // allows the usage of primitive types
    };

    private final String rootPackage;

    private final JavaClasses allClasses;

    private final JavaClasses productionClasses;

    protected HexagonalArchitectureTest(String rootPackage) {
        this.rootPackage = rootPackage;
        this.allClasses = new ClassFileImporter().importPackages(rootPackage);
        this.productionClasses =
            new ClassFileImporter()
                .withImportOption(new DoNotIncludeTests())
                .importPackages(rootPackage);
    }

    @Test
    @DisplayName("hexagonal architecture should be respected")
    void hexagonalArchitectureShouldBeRespected() {
        Architectures.LayeredArchitecture architectureRule =
            Architectures
                .layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain")
                .definedBy("..domain..")
                .layer("In-Ports")
                .definedBy("..application.port.inbound..")
                .layer("Out-Ports")
                .definedBy("..application.port.outbound..")
                .layer("In-Adapters")
                .definedBy("..adapter.inbound..")
                .layer("Out-Adapters")
                .definedBy("..adapter.outbound..")
                .layer("Application")
                .definedBy("..application.service..")
                .whereLayer("In-Ports")
                .mayOnlyBeAccessedByLayers("Application", "In-Adapters")
                .whereLayer("Out-Ports")
                .mayOnlyBeAccessedByLayers("Application", "Out-Adapters")
                .whereLayer("In-Adapters")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Out-Adapters")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Application")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Domain")
                .mayNotAccessAnyLayer()
                .ensureAllClassesAreContainedInArchitectureIgnoring(
                    rootPackage,
                    rootPackage + ".architecture",
                    rootPackage + ".adapter.process..");

        // Production code only: test classes (process tests, fixtures) live outside the layers.
        architectureRule.check(productionClasses);
    }

    @Nested
    class DomainTests {

        @Test
        @DisplayName("domain layer is technology neutral")
        void domainLayerIsTechnologyNeutral() {
            ArchRuleDefinition
                .classes()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(DOMAIN_ALLOWED_PACKAGES)
                .because("The domain must not depend on any framework or infrastructure code")
                .check(productionClasses);
        }
    }

    @Nested
    class GeneralPortTests {

        @Test
        @DisplayName("application ports should be interfaces")
        void applicationPortsShouldBeInterfaces() {
            ArchRuleDefinition
                .classes()
                .that()
                .resideInAPackage("..application.port.inbound..")
                .or()
                .resideInAPackage("..application.port.outbound..")
                .and()
                .areTopLevelClasses()
                .should()
                .beInterfaces()
                .because("All ports should be defined as interfaces")
                .check(allClasses);
        }

        @Test
        @DisplayName("ports should not depend on services")
        void portsShouldNotDependOnServices() {
            ArchRuleDefinition
                .noClasses()
                .that()
                .resideInAPackage("..application.port..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..application.service..")
                .because("Ports decouple adapters from the application services and must stay independent")
                .check(allClasses);
        }
    }

    @Nested
    class ApplicationTests {

        @Test
        @DisplayName("application layer only orchestrates")
        void applicationLayerOnlyOrchestrates() {
            ArchRuleDefinition
                .classes()
                .that()
                .resideInAPackage("..application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(APPLICATION_ALLOWED_PACKAGES)
                .because("The application layer only orchestrates; infrastructure belongs in adapters")
                .check(productionClasses);
        }

        @Test
        @DisplayName("application classes reside in service or port sub-packages")
        void applicationClassesResideInServiceOrPortSubPackages() {
            ArchRuleDefinition
                .classes()
                .that()
                .resideInAPackage("..application..")
                .should()
                .resideInAnyPackage("..application.service..", "..application.port..")
                .because("The application layer is structured into services and ports")
                .check(allClasses);
        }

        @Test
        @DisplayName("application service should implement exactly one use-case")
        void applicationServiceShouldImplementExactlyOneUseCase() {
            ArchRuleDefinition
                .classes()
                .that()
                .resideInAPackage("..application.service..")
                .should(InterfaceImplementationConditions.implementExactlyOneInterfaceFrom("application.port.inbound"))
                .because("application services must implement at least one inbound port")
                .check(productionClasses);
        }

        @Test
        @DisplayName("application service should not depends on other application services")
        void applicationServiceShouldNotDependOnOtherApplicationServices() {
            ArchRuleDefinition
                .noClasses()
                .that()
                .resideInAPackage("..application.service..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..application.service..")
                .because("Application services should not depend on each other")
                .check(productionClasses);
        }

        @Test
        @DisplayName("application service should not use any inbound port")
        void applicationServiceShouldNotUseAnyInboundPort() {
            ArchRuleDefinition
                .noClasses()
                .that()
                .resideInAPackage("..application.service..")
                .should()
                .accessClassesThat(resideInAPackage("..application.port.inbound..").and(INTERFACES))
                .because("A service may implement its own use-case but must never call another one")
                .check(productionClasses);
        }
    }

    @Nested
    class AdapterTests {

        @Test
        @DisplayName("adapter classes reside in inbound or outbound sub-packages")
        void adapterClassesResideInInboundOrOutboundSubPackages() {
            ArchRuleDefinition
                .classes()
                .that()
                .resideInAPackage("..adapter..")
                .and()
                .resideOutsideOfPackage("..adapter.process..")
                .should()
                .resideInAnyPackage("..adapter.inbound..", "..adapter.outbound..")
                .because("Adapters are either inbound (driving) or outbound (driven)")
                .check(allClasses);
        }

        @Test
        @DisplayName("out adapters should not reference in ports")
        void outAdaptersShouldNotReferenceInPorts() {
            ArchRuleDefinition
                .noClasses()
                .that()
                .resideInAPackage("..adapter.outbound..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..application.port.inbound..")
                .because("Outbound adapters are driven and must not reach into inbound ports")
                .check(allClasses);
        }

        @Test
        @DisplayName("in adapters should not implement out ports")
        void inAdaptersShouldNotImplementOutPorts() {
            ArchRuleDefinition
                .noClasses()
                .that()
                .resideInAPackage("..adapter.inbound..")
                .should()
                .implement(resideInAPackage("..application.port.outbound.."))
                .because("Inbound adapters are driving and must not implement outbound ports")
                .check(allClasses);
        }

        @Test
        @DisplayName("in adapters should only offer one use-case or query")
        void inAdaptersShouldOnlyOfferOneUseCaseOrQuery() {
            ArchRuleDefinition
                .classes()
                .that()
                .resideInAPackage("..adapter.inbound..")
                .should(ONLY_FULFIL_ONE_USE_CASE)
                .because("In-adapters should implement exactly one use-case or query")
                .check(productionClasses);
        }
    }
}
