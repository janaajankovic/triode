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
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule sharedDependsOnNoFeatureModule = noClasses()
            .that()
            .resideInAPackage("dev.triode.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.triode.tickets..",
                    "dev.triode.triage..",
                    "dev.triode.knowledge..",
                    "dev.triode.review..",
                    "dev.triode.integrations..")
            .because("feature modules use shared, never the other way round (ADR 0010)")
            .allowEmptyShould(false);

    /** Domain packages extend types from shared.api (e.g. DomainException), so those must be framework-free too. */
    @ArchTest
    static final ArchRule sharedApiIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("dev.triode.shared.api..")
            .and()
            .doNotHaveSimpleName("package-info") // carries Spring Modulith's @NamedInterface
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "software.amazon..", "jakarta..")
            .because("domain packages extend shared.api types and must stay pure Java")
            .allowEmptyShould(false);
}
