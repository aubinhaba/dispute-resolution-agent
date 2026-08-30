package com.bino.dra.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class CleanArchitectureTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter().importPackages("com.bino.dra");

    @Test
    void domain_depends_on_no_framework_and_no_outer_layer() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bino.dra.application..",
                        "com.bino.dra.adapter..",
                        "org.springframework..",
                        "org.springframework.ai..",
                        "com.fasterxml.jackson.."
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
                        "com.bino.dra.adapter..",
                        "org.springframework.ai.."
                )
                .because("use cases depend on abstract ports; details (LLM, MCP, I/O) live in out adapters (ADR-0001, ADR-0007)")
                .check(CLASSES);
    }

    @Test
    void only_adapters_know_about_spring_ai() {
        noClasses()
                .that().resideOutsideOfPackage("com.bino.dra.adapter..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.ai..")
                .because("the LLM, the vector store and Spring AI Documents are implementation "
                        + "details confined to out adapters (ADR-0009, ADR-0010)")
                .check(CLASSES);
    }
}
