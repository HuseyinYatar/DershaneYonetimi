package com.dershane.taskservice.roster.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.dershane.taskservice.roster.application.port.in.LinkGuardianUseCase;
import com.dershane.taskservice.roster.application.port.in.RecordMembershipAddedCommand;
import com.dershane.taskservice.roster.application.port.in.RecordMembershipAddedUseCase;
import com.dershane.taskservice.roster.application.port.in.RecordMembershipRemovedUseCase;
import com.dershane.taskservice.roster.application.port.in.UnlinkGuardianUseCase;
import com.dershane.taskservice.shared.adapter.in.event.EventHandler;
import com.dershane.taskservice.shared.application.Actor;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UserEventHandlersTest {

  /** Tests run with the module directory (services/task) as working directory. */
  private static final Path USER_EVENTS_DIR = Path.of("..", "..", "shared", "events", "user");

  private static final UUID MEMBERSHIP = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID STUDENT = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID CLASSROOM = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID BRANCH = UUID.fromString("44444444-4444-4444-8444-444444444444");
  private static final UUID GUARDIAN = UUID.fromString("55555555-5555-4555-8555-555555555555");

  /** One valid payload per event type, as the user service publishes it. */
  private static final Map<String, String> SAMPLES =
      Map.of(
          "StudentAddedToClassroom",
          """
          {"membershipId":"%s","studentId":"%s","classroomId":"%s","branchId":"%s",
           "studentNumber":"1042","fullName":"Ali Kaya","startDate":"2026-09-14"}
          """
              .formatted(MEMBERSHIP, STUDENT, CLASSROOM, BRANCH),
          "StudentRemovedFromClassroom",
          """
          {"membershipId":"%s","studentId":"%s","classroomId":"%s","branchId":"%s",
           "endDate":"2026-10-30"}
          """
              .formatted(MEMBERSHIP, STUDENT, CLASSROOM, BRANCH),
          "GuardianLinked",
          """
          {"guardianId":"%s","studentId":"%s","relationship":"MOTHER"}
          """
              .formatted(GUARDIAN, STUDENT),
          "GuardianUnlinked",
          """
          {"guardianId":"%s","studentId":"%s"}
          """
              .formatted(GUARDIAN, STUDENT));

  @Mock private RecordMembershipAddedUseCase recordAdded;
  @Mock private RecordMembershipRemovedUseCase recordRemoved;
  @Mock private LinkGuardianUseCase linkGuardian;
  @Mock private UnlinkGuardianUseCase unlinkGuardian;

  private final JsonMapper json = JsonMapper.builder().build();
  private List<EventHandler> handlers;

  @BeforeEach
  void setUp() {
    handlers =
        List.of(
            new StudentAddedToClassroomHandler(recordAdded, json),
            new StudentRemovedFromClassroomHandler(recordRemoved, json),
            new GuardianLinkedHandler(linkGuardian, json),
            new GuardianUnlinkedHandler(unlinkGuardian, json));
  }

  private void handle(String eventType) {
    handlers.stream()
        .filter(handler -> handler.eventType().equals(eventType))
        .findFirst()
        .orElseThrow()
        .handle(eventType, json.readTree(SAMPLES.get(eventType)));
  }

  @Test
  void studentAddedRecordsTheMembershipAsTheSystem() {
    handle("StudentAddedToClassroom");

    ArgumentCaptor<RecordMembershipAddedCommand> command =
        ArgumentCaptor.forClass(RecordMembershipAddedCommand.class);
    verify(recordAdded)
        .recordAdded(org.mockito.ArgumentMatchers.eq(Actor.SYSTEM), command.capture());
    assertThat(command.getValue())
        .isEqualTo(
            new RecordMembershipAddedCommand(
                MEMBERSHIP,
                STUDENT,
                CLASSROOM,
                BRANCH,
                "1042",
                "Ali Kaya",
                LocalDate.of(2026, 9, 14)));
  }

  @Test
  void studentRemovedClosesTheMembershipOnTheEndDate() {
    handle("StudentRemovedFromClassroom");

    verify(recordRemoved).recordRemoved(Actor.SYSTEM, MEMBERSHIP, LocalDate.of(2026, 10, 30));
  }

  @Test
  void guardianLinkedLinksTheGuardian() {
    handle("GuardianLinked");

    verify(linkGuardian).link(Actor.SYSTEM, GUARDIAN, STUDENT);
  }

  @Test
  void guardianUnlinkedNamesTheGuardianSoAStaleEventCannotRemoveAnotherLink() {
    handle("GuardianUnlinked");

    verify(unlinkGuardian).unlink(Actor.SYSTEM, STUDENT, GUARDIAN);
  }

  @Test
  void handlesExactlyTheSampledEventTypes() {
    assertThat(handlers.stream().map(EventHandler::eventType))
        .containsExactlyInAnyOrderElementsOf(SAMPLES.keySet());
  }

  /**
   * The samples above stay valid only while the shared schemas do, so a schema change fails here.
   */
  @ParameterizedTest
  @MethodSource("sampleTypes")
  void theSamplesMatchTheSharedSchemas(String eventType) throws IOException {
    SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    try (InputStream input =
        Files.newInputStream(USER_EVENTS_DIR.resolve(eventType + ".schema.json"))) {
      Schema schema = registry.getSchema(input);
      assertThat(schema.validate(json.readTree(SAMPLES.get(eventType)))).isEmpty();
    }
  }

  static java.util.Set<String> sampleTypes() {
    return SAMPLES.keySet();
  }
}
