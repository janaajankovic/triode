package dev.triode;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the module structure: no cycles between modules and no access to another module's
 * internal packages. Also writes module diagrams to target/spring-modulith-docs.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(TriodeApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
