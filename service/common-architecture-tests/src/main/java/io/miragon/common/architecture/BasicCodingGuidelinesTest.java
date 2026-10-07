package io.miragon.common.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.GeneralCodingRules;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * ArchUnit half of the combined suite: general coding guidelines that rely on the resolved bytecode
 * graph (package structure, freedom of cycles, no {@code System.out}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BasicCodingGuidelinesTest {

    private final String pathFromRoot;

    private final JavaClasses allClasses;

    private final JavaClasses productionClasses;

    protected BasicCodingGuidelinesTest(String pathFromRoot) {
        this.pathFromRoot = pathFromRoot;
        this.allClasses = new ClassFileImporter().importPackages(pathFromRoot);
        this.productionClasses =
            new ClassFileImporter()
                .withImportOption(new DoNotIncludeTests())
                .importPackages(pathFromRoot);
    }

    @Test
    @DisplayName("each class has package declaration")
    void eachClassHasPackageDeclaration() {
        ArchRuleDefinition
            .classes()
            .should()
            .resideInAnyPackage(pathFromRoot + "..")
            .because("All classes should be in the specified package structure")
            .check(allClasses);
    }

    @Test
    @DisplayName("classes are free of cycles")
    void classesAreFreeOfCycles() {
        SlicesRuleDefinition
            .slices()
            .matching(pathFromRoot + ".(**)")
            .should()
            .beFreeOfCycles()
            .because("Classes should not have circular dependencies")
            .check(allClasses);
    }

    @Test
    @DisplayName("production code does not use println or System out")
    void productionCodeDoesNotUsePrintlnOrSystemOut() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("Use a logger instead of println or System.out for diagnostic output")
            .check(productionClasses);
    }
}
