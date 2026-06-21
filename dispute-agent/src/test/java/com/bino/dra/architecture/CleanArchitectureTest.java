package com.bino.dra.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture guardrail — Clean Architecture (see ADR-0007). Dependencies point inward, from the
 * innermost layer outward: {@code domain} (entities) → {@code application} (use cases + ports) →
 * {@code adapter} (LLM, MCP, I/O). A layer may depend only on a more inner one. ArchUnit fails the
 * build on any violation.
 */
class CleanArchitectureTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter().importPackages("com.bino.dra");

    @Test
    void domain_depends_on_no_framework_and_no_outer_layer() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bino.dra.application..",  // an entity does not know the use cases
                        "com.bino.dra.adapter..",      // nor the adapters
                        "org.springframework..",       // nor Spring
                        "org.springframework.ai..",    // nor the ChatClient / advisors / MCP
                        "com.fasterxml.jackson.."      // nor JSON serialization
                )
                .because("the domain is the innermost layer: no use cases, no adapters, no framework (ADR-0007)")
                .check(CLASSES);
    }

    @Test
    void application_depends_on_no_adapter_and_no_spring_ai() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bino.dra.adapter..",      // a use case does not know the concrete implementation
                        "org.springframework.ai.."     // the LLM plugs in via a port, inside an adapter
                )
                .because("use cases depend on abstract ports; details (LLM, MCP, I/O) live in out adapters (ADR-0001, ADR-0007)")
                .check(CLASSES);
    }
}
