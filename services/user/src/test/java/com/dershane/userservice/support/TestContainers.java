package com.dershane.userservice.support;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.floci.testcontainers.FlociContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Containers shared by all integration tests in one JVM. Started on first use and removed by
 * Testcontainers when the JVM exits. Images match local/docker-compose.yml and production.
 */
public final class TestContainers {

  public static final String REGION = "eu-central-1";

  private static PostgreSQLContainer postgres;
  private static FlociContainer floci;
  private static KeycloakContainer keycloak;

  private TestContainers() {}

  public static synchronized PostgreSQLContainer postgres() {
    if (postgres == null) {
      postgres = new PostgreSQLContainer("postgres:17-alpine");
      postgres.start();
    }
    return postgres;
  }

  public static synchronized FlociContainer floci() {
    if (floci == null) {
      floci = new FlociContainer("floci/floci:2.3.0").withRegion(REGION);
      floci.start();
    }
    return floci;
  }

  /** Keycloak with the realm from local/keycloak/dershane-realm.json. */
  public static synchronized KeycloakContainer keycloak() {
    if (keycloak == null) {
      keycloak =
          new KeycloakContainer("quay.io/keycloak/keycloak:26.8.1")
              .withRealmImportFile("keycloak/dershane-realm.json");
      keycloak.start();
    }
    return keycloak;
  }
}
