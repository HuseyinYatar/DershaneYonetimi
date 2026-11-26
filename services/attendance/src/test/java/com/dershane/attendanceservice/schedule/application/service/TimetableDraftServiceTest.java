package com.dershane.attendanceservice.schedule.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.schedule.application.port.in.CreateScheduleCommand;
import com.dershane.attendanceservice.schedule.application.port.in.CreateScheduleUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateTimetableDraftCommand;
import com.dershane.attendanceservice.schedule.application.port.in.MoveDraftLessonCommand;
import com.dershane.attendanceservice.schedule.application.port.in.ScheduleView;
import com.dershane.attendanceservice.schedule.application.port.in.TimetableDraftView;
import com.dershane.attendanceservice.schedule.application.port.out.BranchScheduleLockPort;
import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableDraftPort;
import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableSettingPort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveTimetableDraftPort;
import com.dershane.attendanceservice.schedule.application.port.out.ScheduleQueryPort;
import com.dershane.attendanceservice.schedule.application.port.out.UserDirectoryPort;
import com.dershane.attendanceservice.schedule.application.port.out.UserDirectoryPort.BranchAssignment;
import com.dershane.attendanceservice.schedule.domain.TimetableDraft;
import com.dershane.attendanceservice.schedule.domain.TimetableDraft.DraftLesson;
import com.dershane.attendanceservice.schedule.domain.TimetableSetting;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TimetableDraftServiceTest {

  @Mock private LoadTimetableSettingPort loadSetting;
  @Mock private ScheduleQueryPort scheduleQuery;
  @Mock private UserDirectoryPort userDirectory;
  @Mock private LoadTimetableDraftPort loadDraft;
  @Mock private SaveTimetableDraftPort saveDraft;
  @Mock private CreateScheduleUseCase createSchedule;
  @Mock private BranchScheduleLockPort branchLock;

  private final UUID branchId = UUID.randomUUID();
  private final UUID classroom = UUID.randomUUID();
  private final UUID teacher = UUID.randomUUID();
  private final UUID subject = UUID.randomUUID();
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");

  private TimetableDraftService service;

  @BeforeEach
  void setUp() {
    service =
        new TimetableDraftService(
            loadSetting,
            scheduleQuery,
            userDirectory,
            loadDraft,
            saveDraft,
            createSchedule,
            new BranchAccessPolicy(),
            branchLock);
  }

  private static LocalTime t(String hhmm) {
    return LocalTime.parse(hhmm);
  }

  private void existingSchedules(ScheduleView... views) {
    when(scheduleQuery.findViews(eq(branchId), isNull(), isNull(), eq(true), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(views)));
  }

  private ScheduleView schedule(
      UUID classroomId, UUID teacherId, DayOfWeek day, String from, String to) {
    return new ScheduleView(
        UUID.randomUUID(),
        branchId,
        classroomId,
        teacherId,
        subject,
        day,
        t(from),
        t(to),
        LocalDate.of(2026, 11, 16),
        null,
        true,
        null,
        null);
  }

  private DraftLesson lesson(UUID classroomId, DayOfWeek day, String from, String to) {
    return new DraftLesson(UUID.randomUUID(), classroomId, teacher, subject, day, t(from), t(to));
  }

  private TimetableDraft draftWith(DraftLesson... lessons) {
    TimetableDraft draft = TimetableDraft.create(branchId, List.of(lessons), List.of());
    when(loadDraft.findByBranchId(branchId)).thenReturn(Optional.of(draft));
    return draft;
  }

  /** The user service still knows the assignment every lesson of the tests names. */
  private void assignmentsStillExist() {
    when(userDirectory.loadBranchAssignments(branchId))
        .thenReturn(List.of(new BranchAssignment(teacher, classroom, subject, 4)));
  }

  private void defaultGrid() {
    when(loadSetting.findByBranchId(branchId)).thenReturn(Optional.empty());
  }

  private static DomainException thrownBy(Runnable action) {
    try {
      action.run();
    } catch (DomainException ex) {
      return ex;
    }
    throw new AssertionError("expected a DomainException");
  }

  @Test
  void generateReplacesTheDraftWithOneThatCoversWhatIsStillNeeded() {
    defaultGrid();
    existingSchedules(schedule(classroom, teacher, DayOfWeek.MONDAY, "08:10", "09:00"));
    when(userDirectory.loadBranchAssignments(branchId))
        .thenReturn(List.of(new BranchAssignment(teacher, classroom, subject, 4)));
    when(saveDraft.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TimetableDraftView view =
        service.generate(admin, new GenerateTimetableDraftCommand(null, null));

    InOrder order = inOrder(branchLock, saveDraft);
    order.verify(branchLock).lock(branchId);
    order.verify(saveDraft).deleteByBranchId(branchId);
    order.verify(saveDraft).save(any());
    assertThat(view.missing()).isEmpty();
    // 4 hours a week, 1 already on Monday: 3 hours are proposed. A lesson of k hours lasts
    // k * 50 + (k - 1) * 10 minutes on the default grid, so its hours are (minutes + 10) / 60.
    long proposedHours =
        view.lessons().stream()
            .mapToLong(l -> (Duration.between(l.startsAt(), l.endsAt()).toMinutes() + 10) / 60)
            .sum();
    assertThat(proposedHours).isEqualTo(3);
    assertThat(view.lessons()).allMatch(l -> l.startsAt().getMinute() % 10 == 0);
  }

  @Test
  void generateStopsAndKeepsTheOldDraftWhenTheUserServiceIsDown() {
    defaultGrid();
    when(userDirectory.loadBranchAssignments(branchId))
        .thenThrow(new DomainException(ErrorCode.USER_SERVICE_UNAVAILABLE));

    DomainException thrown =
        thrownBy(() -> service.generate(admin, new GenerateTimetableDraftCommand(null, null)));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.USER_SERVICE_UNAVAILABLE);
    verify(saveDraft, never()).deleteByBranchId(any());
    verify(saveDraft, never()).save(any());
  }

  @Test
  void generateWithNothingToPlaceReturnsAnEmptyDraft() {
    defaultGrid();
    existingSchedules();
    when(userDirectory.loadBranchAssignments(branchId))
        .thenReturn(List.of(new BranchAssignment(teacher, classroom, subject, 0)));
    when(saveDraft.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TimetableDraftView view =
        service.generate(admin, new GenerateTimetableDraftCommand(null, null));

    assertThat(view.lessons()).isEmpty();
    assertThat(view.missing()).isEmpty();
  }

  @Test
  void aBranchAdminCannotPlanAnotherBranch() {
    DomainException thrown =
        thrownBy(
            () ->
                service.generate(
                    admin, new GenerateTimetableDraftCommand(UUID.randomUUID(), null)));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
    verify(userDirectory, never()).loadBranchAssignments(any());
  }

  @Test
  void aSuperAdminWithoutABranchMustNameOne() {
    Actor superAdmin = actor(UUID.randomUUID(), null, "SUPER_ADMIN");

    DomainException thrown = thrownBy(() -> service.current(superAdmin, null));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void currentWithoutADraftIsNotFound() {
    when(loadDraft.findByBranchId(branchId)).thenReturn(Optional.empty());

    DomainException thrown = thrownBy(() -> service.current(admin, null));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.TIMETABLE_DRAFT_NOT_FOUND);
  }

  @Test
  void movingALessonToFreeHoursSavesIt() {
    defaultGrid();
    existingSchedules();
    DraftLesson lesson = lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00");
    draftWith(lesson);

    TimetableDraftView.Lesson moved =
        service.move(
            admin,
            new MoveDraftLessonCommand(
                null, lesson.id(), DayOfWeek.TUESDAY, t("09:10"), t("11:00")));

    assertThat(moved.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
    ArgumentCaptor<TimetableDraft> saved = ArgumentCaptor.forClass(TimetableDraft.class);
    verify(saveDraft).save(saved.capture());
    assertThat(saved.getValue().requireLesson(lesson.id()).startsAt()).isEqualTo(t("09:10"));
  }

  @Test
  void movingOntoAnotherLessonOfTheClassroomIsRejected() {
    defaultGrid();
    existingSchedules(schedule(classroom, UUID.randomUUID(), DayOfWeek.MONDAY, "09:10", "10:00"));
    DraftLesson lesson = lesson(classroom, DayOfWeek.TUESDAY, "08:10", "09:00");
    draftWith(lesson);

    DomainException thrown =
        thrownBy(
            () ->
                service.move(
                    admin,
                    new MoveDraftLessonCommand(
                        null, lesson.id(), DayOfWeek.MONDAY, t("09:10"), t("10:00"))));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.CLASSROOM_BUSY);
    verify(saveDraft, never()).save(any());
  }

  @Test
  void movingOntoAnotherClassroomsLessonOfTheSameTeacherIsRejected() {
    defaultGrid();
    existingSchedules(schedule(UUID.randomUUID(), teacher, DayOfWeek.MONDAY, "09:10", "10:00"));
    DraftLesson lesson = lesson(classroom, DayOfWeek.TUESDAY, "08:10", "09:00");
    draftWith(lesson);

    DomainException thrown =
        thrownBy(
            () ->
                service.move(
                    admin,
                    new MoveDraftLessonCommand(
                        null, lesson.id(), DayOfWeek.MONDAY, t("09:10"), t("10:00"))));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.TEACHER_BUSY);
  }

  @Test
  void aLessonCannotSitOnTheLunchHour() {
    when(loadSetting.findByBranchId(branchId))
        .thenReturn(Optional.of(new TimetableSetting(branchId, 1L, t("08:10"), 50, 10, 12, 2)));
    existingSchedules();
    DraftLesson lesson = lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00");
    draftWith(lesson);

    DomainException thrown =
        thrownBy(
            () ->
                service.move(
                    admin,
                    new MoveDraftLessonCommand(
                        null, lesson.id(), DayOfWeek.MONDAY, t("09:10"), t("10:00"))));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.LUNCH_BREAK_SLOT);
  }

  @Test
  void aLessonMustLineUpWithTheGrid() {
    defaultGrid();
    existingSchedules();
    DraftLesson lesson = lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00");
    draftWith(lesson);

    DomainException thrown =
        thrownBy(
            () ->
                service.move(
                    admin,
                    new MoveDraftLessonCommand(
                        null, lesson.id(), DayOfWeek.MONDAY, t("09:00"), t("09:50"))));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.LESSON_OUTSIDE_GRID);
  }

  @Test
  void movingAnUnknownLessonIsNotFound() {
    draftWith(lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00"));

    DomainException thrown =
        thrownBy(
            () ->
                service.move(
                    admin,
                    new MoveDraftLessonCommand(
                        null, UUID.randomUUID(), DayOfWeek.MONDAY, t("09:10"), t("10:00"))));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.DRAFT_LESSON_NOT_FOUND);
  }

  @Test
  void removingALessonRewritesTheDraftWithoutIt() {
    DraftLesson first = lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00");
    DraftLesson second = lesson(classroom, DayOfWeek.TUESDAY, "08:10", "09:00");
    draftWith(first, second);

    service.remove(admin, null, first.id());

    ArgumentCaptor<TimetableDraft> saved = ArgumentCaptor.forClass(TimetableDraft.class);
    verify(saveDraft).save(saved.capture());
    assertThat(saved.getValue().getLessons()).containsExactly(second);
  }

  @Test
  void discardingDeletesTheBranchDraft() {
    service.discard(admin, null);

    verify(saveDraft).deleteByBranchId(branchId);
  }

  @Test
  void commitOpensOneScheduleForEveryLessonAndDropsTheDraft() {
    assignmentsStillExist();
    defaultGrid();
    existingSchedules();
    draftWith(
        lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00"),
        lesson(classroom, DayOfWeek.TUESDAY, "09:10", "11:00"));

    int created = service.commit(admin, null, LocalDate.of(2026, 11, 23));

    assertThat(created).isEqualTo(2);
    ArgumentCaptor<CreateScheduleCommand> commands =
        ArgumentCaptor.forClass(CreateScheduleCommand.class);
    verify(createSchedule, times(2)).create(eq(admin), commands.capture());
    assertThat(commands.getAllValues())
        .allSatisfy(
            command -> {
              assertThat(command.branchId()).isEqualTo(branchId);
              assertThat(command.validFrom()).isEqualTo(LocalDate.of(2026, 11, 23));
              assertThat(command.validTo()).isNull();
            });
    verify(saveDraft).deleteByBranchId(branchId);
  }

  @Test
  void commitFailureLeavesTheDraftAndPropagatesSoTheTransactionRollsBack() {
    assignmentsStillExist();
    defaultGrid();
    existingSchedules();
    draftWith(
        lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00"),
        lesson(classroom, DayOfWeek.TUESDAY, "09:10", "11:00"));
    when(createSchedule.create(any(), any()))
        .thenReturn(null)
        .thenThrow(new DomainException(ErrorCode.BRANCH_MISMATCH));

    DomainException thrown =
        thrownBy(() -> service.commit(admin, null, LocalDate.of(2026, 11, 23)));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.BRANCH_MISMATCH);
    verify(saveDraft, never()).deleteByBranchId(any());
  }

  @Test
  void commitRevalidatesAgainstTheTimetableAsItIsNowAndWritesNothingOnAClash() {
    assignmentsStillExist();
    defaultGrid();
    existingSchedules(schedule(classroom, UUID.randomUUID(), DayOfWeek.MONDAY, "08:10", "09:00"));
    draftWith(lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00"));

    DomainException thrown =
        thrownBy(() -> service.commit(admin, null, LocalDate.of(2026, 11, 23)));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.CLASSROOM_BUSY);
    verify(createSchedule, never()).create(any(), any());
    verify(saveDraft, never()).deleteByBranchId(any());
  }

  @Test
  void commitRefusesADraftWhoseAssignmentIsGoneAndKeepsIt() {
    when(userDirectory.loadBranchAssignments(branchId)).thenReturn(List.of());
    draftWith(lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00"));

    DomainException thrown =
        thrownBy(() -> service.commit(admin, null, LocalDate.of(2026, 11, 23)));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.TIMETABLE_DRAFT_STALE);
    verify(createSchedule, never()).create(any(), any());
    verify(saveDraft, never()).deleteByBranchId(any());
  }

  @Test
  void commitRefusesADraftWhenOnlyOneOfItsAssignmentsIsGone() {
    when(userDirectory.loadBranchAssignments(branchId))
        .thenReturn(List.of(new BranchAssignment(teacher, classroom, subject, 4)));
    draftWith(
        lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00"),
        lesson(UUID.randomUUID(), DayOfWeek.TUESDAY, "08:10", "09:00"));

    DomainException thrown =
        thrownBy(() -> service.commit(admin, null, LocalDate.of(2026, 11, 23)));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.TIMETABLE_DRAFT_STALE);
    verify(createSchedule, never()).create(any(), any());
  }

  @Test
  void commitAsksTheUserServiceBeforeTakingTheBranchLock() {
    assignmentsStillExist();
    defaultGrid();
    existingSchedules();
    draftWith(lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00"));

    service.commit(admin, null, LocalDate.of(2026, 11, 23));

    InOrder order = inOrder(userDirectory, branchLock, createSchedule, saveDraft);
    order.verify(userDirectory).loadBranchAssignments(branchId);
    order.verify(branchLock).lock(branchId);
    order.verify(createSchedule).create(eq(admin), any());
    order.verify(saveDraft).deleteByBranchId(branchId);
  }

  @Test
  void aSecondCommitFindsNoDraftAndOpensNothingMore() {
    assignmentsStillExist();
    defaultGrid();
    existingSchedules();
    TimetableDraft draft =
        TimetableDraft.create(
            branchId, List.of(lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00")), List.of());
    when(loadDraft.findByBranchId(branchId))
        .thenReturn(Optional.of(draft))
        .thenReturn(Optional.empty());

    service.commit(admin, null, LocalDate.of(2026, 11, 23));
    DomainException thrown =
        thrownBy(() -> service.commit(admin, null, LocalDate.of(2026, 11, 23)));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.TIMETABLE_DRAFT_NOT_FOUND);
    verify(branchLock, times(2)).lock(branchId);
    verify(createSchedule, times(1)).create(any(), any());
  }

  @Test
  void commitIgnoresASchedulePeriodThatEndsBeforeTheDraftStarts() {
    assignmentsStillExist();
    defaultGrid();
    existingSchedules(
        new ScheduleView(
            UUID.randomUUID(),
            branchId,
            classroom,
            UUID.randomUUID(),
            subject,
            DayOfWeek.MONDAY,
            t("08:10"),
            t("09:00"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30),
            true,
            null,
            null));
    draftWith(lesson(classroom, DayOfWeek.MONDAY, "08:10", "09:00"));

    int created = service.commit(admin, null, LocalDate.of(2026, 11, 23));

    assertThat(created).isEqualTo(1);
  }
}
