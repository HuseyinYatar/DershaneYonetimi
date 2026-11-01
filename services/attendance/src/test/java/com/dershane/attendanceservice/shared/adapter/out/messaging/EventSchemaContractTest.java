package com.dershane.attendanceservice.shared.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.attendanceservice.shared.application.event.EventType;
import com.dershane.attendanceservice.shared.application.event.IntegrationEvent;
import com.dershane.attendanceservice.shared.application.event.IntegrationEvent.StudentAbsent;
import com.dershane.attendanceservice.shared.application.event.IntegrationEvent.StudentExcusedByTeacher;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract test: every event this service publishes must validate against its schema in {@code
 * shared/events}. Python consumers generate their models from the same files, so a drift here would
 * break them silently.
 */
class EventSchemaContractTest {

  /** Tests run with the module directory (services/attendance) as working directory. */
  private static final Path EVENTS_DIR = Path.of("..", "..", "shared", "events");

  private static final UUID ID = UUID.fromString("0f0a6f0e-5b1e-4d0c-9a51-0d1c2b3a4f5e");

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final SchemaRegistry registry =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  static Stream<IntegrationEvent> sampleEvents() {
    LocalDate date = LocalDate.of(2026, 10, 17);
    return Stream.of(
        new StudentAbsent(ID, ID, ID, ID, ID, ID, date, LocalTime.of(9, 0), LocalTime.of(9, 40)),
        new StudentExcusedByTeacher(
            ID, ID, ID, ID, ID, ID, date, LocalTime.of(9, 0), LocalTime.of(9, 40)));
  }

  @ParameterizedTest
  @MethodSource("sampleEvents")
  void publishedEventMatchesEnvelopeAndDataSchemas(IntegrationEvent event) throws IOException {
    EventEnvelope envelope =
        new EventEnvelope(
            ID,
            event.type().eventName(),
            EventEnvelope.SCHEMA_VERSION,
            Instant.parse("2026-10-17T07:00:00.123Z"),
            EventEnvelope.SOURCE,
            event);
    JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(envelope));

    assertThat(validate(EVENTS_DIR.resolve("envelope.schema.json"), json)).isEmpty();
    Path dataSchema =
        EVENTS_DIR.resolve("attendance").resolve(event.type().eventName() + ".schema.json");
    assertThat(validate(dataSchema, json.get("data"))).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(EventType.class)
  void everyEventTypeHasASchema(EventType type) {
    assertThat(EVENTS_DIR.resolve("attendance").resolve(type.eventName() + ".schema.json"))
        .exists();
  }

  @Test
  void internalHelpersAreNotSerialized() {
    JsonNode json =
        jsonMapper.readTree(
            jsonMapper.writeValueAsString(
                new StudentAbsent(
                    ID,
                    ID,
                    ID,
                    ID,
                    ID,
                    ID,
                    LocalDate.of(2026, 10, 17),
                    LocalTime.of(9, 0),
                    LocalTime.of(9, 40))));

    assertThat(json.has("type")).isFalse();
    assertThat(json.has("aggregateId")).isFalse();
  }

  private List<String> validate(Path schemaFile, JsonNode instance) throws IOException {
    try (InputStream input = Files.newInputStream(schemaFile)) {
      Schema schema = registry.getSchema(input);
      return schema.validate(instance).stream().map(Error::toString).toList();
    }
  }
}
