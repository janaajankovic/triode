package dev.triode;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architecture rules that Spring Modulith does not cover. */
@AnalyzeClasses(packages = "dev.triode", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("dev.triode..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "software.amazon..", "jakarta.persistence..")
            .because("domain packages hold plain business rules, free of Spring, AWS, Spring AI and JPA")
            .allowEmptyShould(true);
}
