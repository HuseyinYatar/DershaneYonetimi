package com.dershane.taskservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.taskservice.assignment.application.port.in.AssignmentDetailView;
import com.dershane.taskservice.assignment.application.port.in.AssignmentSummaryView;
import com.dershane.taskservice.assignment.application.port.out.AssignmentFilter;
import com.dershane.taskservice.assignment.application.port.out.AssignmentQueryPort;
import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.roster.application.port.in.ActiveMembershipView;
import com.dershane.taskservice.roster.application.port.out.RosterQueryPort;
import com.dershane.taskservice.roster.application.port.out.SaveMembershipReplicaPort;
import com.dershane.taskservice.roster.domain.MembershipReplica;
import com.dershane.taskservice.shared.domain.ConstraintErrorCodes;
import com.dershane.taskservice.shared.domain.DomainException;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.support.TestContainers;
import com.dershane.taskservice.task.application.port.in.TaskView;
import com.dershane.taskservice.task.application.port.out.LoadTaskPort;
import com.dershane.taskservice.task.application.port.out.SaveTaskPort;
import com.dershane.taskservice.task.application.port.out.TaskQueryPort;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import com.dershane.taskservice.task.domain.TaskDraft;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The queries the unit tests can only mock, against the real PostgreSQL schema: array columns, the
 * three-row assignment aggregate, the reminder window, the classroom sub-select, the SQL read model
 * and the constraint that catches concurrent generation. Runs the real Flyway migration; {@code
 * ddl-auto=validate} also checks that the entity mapping matches the schema.
 */
@DataJpaTest(
    includeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.dershane\\.taskservice\\..*\\.adapter\\.out\\.persistence\\..*"))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryQueriesIT {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestContainers.postgres().getJdbcUrl());
    registry.add("spring.datasource.username", () -> TestContainers.postgres().getUsername());
    registry.add("spring.datasource.password", () -> TestContainers.postgres().getPassword());
  }

  @Autowired private SaveTaskPort saveTask;
  @Autowired private LoadTaskPort loadTask;
  @Autowired private TaskQueryPort taskQuery;
  @Autowired private SaveAssignmentPort saveAssignment;
  @Autowired private LoadAssignmentPort loadAssignment;
  @Autowired private AssignmentQueryPort assignmentQuery;
  @Autowired private SaveMembershipReplicaPort saveMemberships;
  @Autowired private RosterQueryPort rosterQuery;
  @Autowired private EntityManager entityManager;

  private final UUID branchId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();

  private Task task(Recurrence recurrence, TargetType targetType, UUID targetId) {
    boolean once = recurrence == Recurrence.ONCE;
    return saveTask.save(
        Task.create(
            new TaskDraft(
                branchId,
                teacherId,
                "Chapter 4 problems",
                "Solve all of them",
                targetType,
                targetId,
                recurrence,
                recurrence == Recurrence.WEEKLY ? Set.of(DayOfWeek.MONDAY) : Set.of(),
                DAY,
                once ? DAY.plusDays(2) : null,
                LocalTime.of(18, 0),
                List.of(UUID.randomUUID())),
            DAY,
            NOW,
            ZoneOffset.UTC));
  }

  private Assignment assignment(Task task, UUID student, Instant dueAt, Instant assignedAt) {
    return saveAssignment.save(Assignment.assign(task.getId(), student, DAY, dueAt, assignedAt));
  }

  /** Makes the pending inserts and updates reach the database and drops the first-level cache. */
  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void taskRoundTripKeepsArraysAndPrunesNothing() {
    Task saved = task(Recurrence.WEEKLY, TargetType.CLASSROOM, classroomId);
    flushAndClear();

    Task loaded = loadTask.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getRecurrenceDays()).containsExactly(DayOfWeek.MONDAY);
    assertThat(loaded.getAttachmentFileIds()).hasSize(1);
    assertThat(loaded.getDueTime()).isEqualTo(LocalTime.of(18, 0));

    loaded.edit("New title", "New text", List.of());
    saveTask.save(loaded);
    flushAndClear();
    assertThat(loadTask.findById(saved.getId()).orElseThrow().getAttachmentFileIds()).isEmpty();
  }

  @Test
  void activeTaskQueriesRespectDatesTargetAndActiveFlag() {
    Task daily = task(Recurrence.DAILY, TargetType.CLASSROOM, classroomId);
    Task other = task(Recurrence.DAILY, TargetType.CLASSROOM, UUID.randomUUID());
    Task stopped = task(Recurrence.DAILY, TargetType.CLASSROOM, classroomId);
    stopped.deactivate();
    saveTask.save(stopped);
    flushAndClear();

    assertThat(loadTask.findActiveOn(DAY))
        .extracting(Task::getId)
        .contains(daily.getId(), other.getId());
    assertThat(loadTask.findActiveOn(DAY)).extracting(Task::getId).doesNotContain(stopped.getId());
    assertThat(loadTask.findActiveOn(DAY.minusDays(1)))
        .extracting(Task::getId)
        .doesNotContain(daily.getId());
    assertThat(loadTask.findActiveTargeting(TargetType.CLASSROOM, classroomId, DAY))
        .extracting(Task::getId)
        .containsExactly(daily.getId());

    Page<TaskView> mine = taskQuery.findViews(branchId, teacherId, true, PageRequest.of(0, 10));
    assertThat(mine.getContent()).extracting(TaskView::id).contains(daily.getId());
    assertThat(mine.getContent()).extracting(TaskView::id).doesNotContain(stopped.getId());
  }

  @Test
  void assignmentAggregateKeepsSubmissionAndEvaluationAndGuardsTheVersion() {
    Task task = task(Recurrence.ONCE, TargetType.STUDENT, UUID.randomUUID());
    UUID student = UUID.randomUUID();
    Assignment created = assignment(task, student, Instant.parse("2026-11-11T18:00:00Z"), NOW);
    flushAndClear();

    Assignment loaded = loadAssignment.findById(created.getId()).orElseThrow();
    loaded.submit("My answer", List.of(UUID.randomUUID()), NOW.plusSeconds(60));
    saveAssignment.save(loaded);
    flushAndClear();

    Assignment submitted = loadAssignment.findById(created.getId()).orElseThrow();
    assertThat(submitted.getStatus()).isEqualTo(AssignmentStatus.SUBMITTED);
    assertThat(submitted.getSubmission().description()).isEqualTo("My answer");
    assertThat(submitted.getVersion()).isGreaterThan(loaded.getVersion());

    submitted.evaluate(teacherId, 90, "Good", NOW.plusSeconds(120));
    saveAssignment.save(submitted);
    flushAndClear();

    AssignmentDetailView detail = assignmentQuery.findDetailById(created.getId()).orElseThrow();
    assertThat(detail.status()).isEqualTo(AssignmentStatus.GRADED);
    assertThat(detail.submission().fileIds()).hasSize(1);
    assertThat(detail.evaluation().score()).isEqualTo(90);
    assertThat(detail.taskTitle()).isEqualTo("Chapter 4 problems");
    assertThat(detail.taskAttachmentFileIds()).hasSize(1);
    assertThat(detail.teacherId()).isEqualTo(teacherId);

    // A copy loaded before the save is stale now.
    assertThatThrownBy(() -> saveAssignment.save(loaded))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
  }

  @Test
  void resubmittingKeepsOneSubmissionRow() {
    Task task = task(Recurrence.ONCE, TargetType.STUDENT, UUID.randomUUID());
    Assignment created =
        assignment(task, UUID.randomUUID(), Instant.parse("2026-11-11T18:00:00Z"), NOW);
    flushAndClear();
    for (String text : List.of("first", "second")) {
      Assignment loaded = loadAssignment.findById(created.getId()).orElseThrow();
      loaded.submit(text, List.of(), NOW.plusSeconds(60));
      saveAssignment.save(loaded);
      flushAndClear();
    }
    AssignmentDetailView detail = assignmentQuery.findDetailById(created.getId()).orElseThrow();
    assertThat(detail.submission().description()).isEqualTo("second");
  }

  @Test
  void uniqueConstraintCatchesTheSameOccurrenceTwice() {
    Task task = task(Recurrence.DAILY, TargetType.CLASSROOM, classroomId);
    UUID student = UUID.randomUUID();
    assignment(task, student, Instant.parse("2026-11-09T18:00:00Z"), NOW);
    flushAndClear();

    assertThatThrownBy(
            () -> {
              assignment(task, student, Instant.parse("2026-11-09T18:00:00Z"), NOW);
              entityManager.flush();
            })
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .hasCauseInstanceOf(ConstraintViolationException.class)
        .cause()
        .extracting(c -> ((ConstraintViolationException) c).getConstraintName())
        .isEqualTo("uk_assignment_task_student_date");
    assertThat(ConstraintErrorCodes.forConstraint("uk_assignment_task_student_date"))
        .contains(ErrorCode.DATA_CONFLICT);
  }

  @Test
  void existingStudentsOfAnOccurrenceAreFound() {
    Task task = task(Recurrence.DAILY, TargetType.CLASSROOM, classroomId);
    UUID first = UUID.randomUUID();
    assignment(task, first, Instant.parse("2026-11-09T18:00:00Z"), NOW);
    flushAndClear();

    assertThat(loadAssignment.findStudentIdsWithAssignment(task.getId(), DAY))
        .containsExactly(first);
    assertThat(loadAssignment.findStudentIdsWithAssignment(task.getId(), DAY.plusDays(1)))
        .isEmpty();
  }

  @Test
  void openAssignmentsOfAClassroomExcludeOtherTargetsAndPastDueDates() {
    Task forClass = task(Recurrence.DAILY, TargetType.CLASSROOM, classroomId);
    Task forOtherClass = task(Recurrence.DAILY, TargetType.CLASSROOM, UUID.randomUUID());
    Task individual = task(Recurrence.DAILY, TargetType.STUDENT, classroomId);
    UUID student = UUID.randomUUID();
    Assignment open = assignment(forClass, student, NOW.plus(Duration.ofHours(5)), NOW);
    assignment(forOtherClass, student, NOW.plus(Duration.ofHours(5)), NOW);
    assignment(individual, student, NOW.plus(Duration.ofHours(5)), NOW);
    Task pastTask = task(Recurrence.WEEKLY, TargetType.CLASSROOM, classroomId);
    assignment(pastTask, student, NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofDays(1)));
    flushAndClear();

    assertThat(loadAssignment.findOpenForClassroom(student, classroomId, NOW))
        .extracting(Assignment::getId)
        .containsExactly(open.getId());
  }

  @Test
  void reminderWindowSkipsFreshAssignmentsAndRemindedOnes() {
    Duration lead = Duration.ofHours(6);
    Task task = task(Recurrence.DAILY, TargetType.CLASSROOM, classroomId);
    Instant dueAt = NOW.plus(Duration.ofHours(3));
    Assignment due = assignment(task, UUID.randomUUID(), dueAt, NOW.minus(Duration.ofHours(20)));
    assignment(task, UUID.randomUUID(), dueAt, NOW.minus(Duration.ofHours(1)));
    Assignment reminded =
        assignment(task, UUID.randomUUID(), dueAt, NOW.minus(Duration.ofHours(20)));
    assignment(
        task, UUID.randomUUID(), NOW.plus(Duration.ofHours(30)), NOW.minus(Duration.ofHours(20)));
    flushAndClear();
    Assignment toMark = loadAssignment.findById(reminded.getId()).orElseThrow();
    toMark.markReminderSent();
    saveAssignment.save(toMark);
    flushAndClear();

    assertThat(loadAssignment.findReminderCandidates(NOW, lead, 10))
        .extracting(Assignment::getId)
        .containsExactly(due.getId());
  }

  @Test
  void overdueCandidatesAreOpenAssignmentsPastTheirDueTime() {
    Task task = task(Recurrence.DAILY, TargetType.CLASSROOM, classroomId);
    Assignment late =
        assignment(task, UUID.randomUUID(), NOW.minusSeconds(60), NOW.minus(Duration.ofDays(1)));
    assignment(task, UUID.randomUUID(), NOW.plusSeconds(60), NOW.minus(Duration.ofDays(1)));
    flushAndClear();

    assertThat(loadAssignment.findOverdueCandidates(NOW, 10))
        .extracting(Assignment::getId)
        .contains(late.getId())
        .hasSize(1);
  }

  @Test
  void readModelFiltersByScopeAndStatus() {
    Task task = task(Recurrence.DAILY, TargetType.CLASSROOM, classroomId);
    UUID student = UUID.randomUUID();
    Assignment mine = assignment(task, student, NOW.plus(Duration.ofHours(5)), NOW);
    assignment(task, UUID.randomUUID(), NOW.plus(Duration.ofHours(5)), NOW);
    flushAndClear();

    Page<AssignmentSummaryView> ofStudent =
        assignmentQuery.findSummaries(
            new AssignmentFilter(student, null, null, null, null, null, null),
            PageRequest.of(0, 10));
    assertThat(ofStudent.getContent())
        .extracting(AssignmentSummaryView::id)
        .containsExactly(mine.getId());
    assertThat(ofStudent.getContent().getFirst().taskTitle()).isEqualTo("Chapter 4 problems");

    Page<AssignmentSummaryView> ofTeacher =
        assignmentQuery.findSummaries(
            new AssignmentFilter(
                null, teacherId, branchId, task.getId(), AssignmentStatus.ASSIGNED, DAY, DAY),
            PageRequest.of(0, 10));
    assertThat(ofTeacher.getTotalElements()).isEqualTo(2);

    Page<AssignmentSummaryView> none =
        assignmentQuery.findSummaries(
            new AssignmentFilter(student, null, null, null, AssignmentStatus.GRADED, null, null),
            PageRequest.of(0, 10));
    assertThat(none.getContent()).isEmpty();
  }

  @Test
  void rosterQueriesFindTheActiveMembershipAndClassroomBranch() {
    UUID student = UUID.randomUUID();
    saveMemberships.save(
        MembershipReplica.open(
            UUID.randomUUID(), classroomId, student, branchId, DAY, "ST1", "Ali Veli"));
    flushAndClear();

    assertThat(rosterQuery.findActiveMembership(student, DAY))
        .contains(new ActiveMembershipView(classroomId, branchId));
    assertThat(rosterQuery.findActiveMembership(student, DAY.minusDays(1))).isEmpty();
    assertThat(rosterQuery.findClassroomBranchOn(classroomId, DAY)).contains(branchId);
    assertThat(rosterQuery.findClassroomBranchOn(UUID.randomUUID(), DAY)).isEmpty();
  }
}
