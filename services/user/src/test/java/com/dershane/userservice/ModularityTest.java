package com.dershane.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Module structure: no cycles, no access to another module's internals, and only the dependencies
 * each module declares in its {@code package-info.java}.
 */
class ModularityTest {

  private final ApplicationModules modules = ApplicationModules.of(UserServiceApplication.class);

  @Test
  void modulesRespectTheirBoundaries() {
    modules.verify();
  }

  /** Diagrams for the report, written to {@code target/spring-modulith-docs}. */
  @Test
  void writesModuleDocumentation() {
    new Documenter(modules).writeDocumentation();
  }
}
