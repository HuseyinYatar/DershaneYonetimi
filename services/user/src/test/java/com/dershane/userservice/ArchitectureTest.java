package com.dershane.userservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import jakarta.persistence.Entity;

/**
 * Layer rules, checked on every build. Each business module (branch, subject, classroom, user,
 * enrollment) and the shared kernel has the same three layers:
 *
 * <ul>
 *   <li>{@code domain}: plain Java rules and value objects; depends on nothing else
 *   <li>{@code application}: use cases and ports; depends on the domain
 *   <li>{@code adapter}: web, persistence, messaging, Keycloak; depends on application and domain,
 *       and adapters never call each other
 * </ul>
 *
 * Module boundaries and cycles between modules are checked by {@link ModularityTest}.
 */
@AnalyzeClasses(
    packages = "com.dershane.userservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String[] BUSINESS_MODULES = {
    "com.dershane.userservice.branch..",
    "com.dershane.userservice.subject..",
    "com.dershane.userservice.classroom..",
    "com.dershane.userservice.user..",
    "com.dershane.userservice.enrollment.."
  };

  @ArchTest
  static final ArchRule ONION_LAYERS =
      Architectures.onionArchitecture()
          .domainModels("..domain..")
          .applicationServices("..application..")
          .adapter("in", "..adapter.in..")
          .adapter("persistence", "..adapter.out.persistence..")
          .adapter("messaging", "..adapter.out.messaging..")
          .adapter("keycloak", "..adapter.out.keycloak..")
          .withOptionalLayers(true);

  @ArchTest
  static final ArchRule DOMAIN_HAS_NO_FRAMEWORK_DEPENDENCIES =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          // package-info only carries the Spring Modulith module annotations.
          .and()
          .haveSimpleNameNotEndingWith("package-info")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "tools.jackson..",
              "com.fasterxml..")
          .because("the domain is plain Java: no Spring, JPA or Jackson");

  @ArchTest
  static final ArchRule CONTROLLERS_DEPEND_ONLY_ON_IN_PORTS =
      noClasses()
          .that()
          .resideInAPackage("..adapter.in.web..")
          .and()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..application.service..", "..application.port.out..", "..adapter.out..")
          .because("controllers talk to use case interfaces only");

  @ArchTest
  static final ArchRule SERVICES_ARE_NOT_PUBLIC_API =
      classes()
          .that()
          .resideInAPackage("..application.service..")
          .should()
          .notBePublic()
          .because("a module's public API is its domain and its in-ports, not its services");

  @ArchTest
  static final ArchRule ENTITIES_LIVE_IN_PERSISTENCE_ADAPTERS =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .resideInAnyPackage(
              "..adapter.out.persistence..",
              "..adapter.out.messaging..",
              "..adapter.in.web.idempotency..")
          .because("JPA entities are persistence details, separate from the domain model");

  @ArchTest
  static final ArchRule SHARED_KERNEL_KNOWS_NO_BUSINESS_MODULE =
      noClasses()
          .that()
          .resideInAPackage("com.dershane.userservice.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BUSINESS_MODULES);

  @ArchTest
  static final ArchRule MODULES_HAVE_NO_CYCLES =
      slices().matching("com.dershane.userservice.(*)..").should().beFreeOfCycles();
}
