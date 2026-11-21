package com.dershane.attendanceservice.schedule.application.service;

import com.dershane.attendanceservice.schedule.application.port.in.CommitTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.CreateScheduleCommand;
import com.dershane.attendanceservice.schedule.application.port.in.CreateScheduleUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.DiscardTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateTimetableDraftCommand;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GetTimetableDraftQuery;
import com.dershane.attendanceservice.schedule.application.port.in.MoveDraftLessonCommand;
import com.dershane.attendanceservice.schedule.application.port.in.MoveDraftLessonUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.RemoveDraftLessonUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.ScheduleView;
import com.dershane.attendanceservice.schedule.application.port.in.TimetableDraftView;
import com.dershane.attendanceservice.schedule.application.port.out.BranchScheduleLockPort;
import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableDraftPort;
import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableSettingPort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveTimetableDraftPort;
import com.dershane.attendanceservice.schedule.application.port.out.ScheduleQueryPort;
import com.dershane.attendanceservice.schedule.application.port.out.UserDirectoryPort;
import com.dershane.attendanceservice.schedule.application.port.out.UserDirectoryPort.BranchAssignment;
import com.dershane.attendanceservice.schedule.domain.ScheduleConflicts;
import com.dershane.attendanceservice.schedule.domain.TimetableDraft;
import com.dershane.attendanceservice.schedule.domain.TimetableDraft.DraftLesson;
import com.dershane.attendanceservice.schedule.domain.TimetableDraft.MissingHours;
import com.dershane.attendanceservice.schedule.domain.TimetableGrid;
import com.dershane.attendanceservice.schedule.domain.TimetablePlanner;
import com.dershane.attendanceservice.schedule.domain.TimetablePlanner.Demand;
import com.dershane.attendanceservice.schedule.domain.TimetablePlanner.Placed;
import com.dershane.attendanceservice.schedule.domain.TimetablePlanner.Plan;
import com.dershane.attendanceservice.schedule.domain.TimetableSetting;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automatic distribution: the planner runs here, the result is kept as the branch's draft, and an
 * admin edits it before saving it as real class schedules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class TimetableDraftService
    implements GenerateTimetableDraftUseCase,
        GetTimetableDraftQuery,
        MoveDraftLessonUseCase,
        RemoveDraftLessonUseCase,
        DiscardTimetableDraftUseCase,
        CommitTimetableDraftUseCase {

  private static final List<DayOfWeek> WORKDAYS =
      List.of(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY);

  private final LoadTimetableSettingPort loadSetting;
  private final ScheduleQueryPort scheduleQuery;
  private final UserDirectoryPort userDirectory;
  private final LoadTimetableDraftPort loadDraft;
  private final SaveTimetableDraftPort saveDraft;
  private final CreateScheduleUseCase createSchedule;
  private final BranchAccessPolicy branchAccess;
  private final BranchScheduleLockPort branchLock;

  @Override
  @Transactional
  @Observed(name = "timetable-draft.generate")
  public TimetableDraftView generate(Actor actor, GenerateTimetableDraftCommand command) {
    UUID branch = branch(actor, command.branchId(), "timetable-draft.generate");
    TimetableGrid grid = gridOf(branch);
    List<DayOfWeek> days =
        command.days() == null || command.days().isEmpty()
            ? WORKDAYS
            : command.days().stream().distinct().toList();
    List<BranchAssignment> assignments = userDirectory.loadBranchAssignments(branch);
    List<Placed> existing =
        activeSchedules(branch).stream()
            .flatMap(
                s ->
                    grid.touchedBy(s.startsAt(), s.endsAt()).stream()
                        .map(
                            span ->
                                new Placed(
                                    s.classroomId(),
                                    s.teacherId(),
                                    s.subjectId(),
                                    s.dayOfWeek(),
                                    span)))
            .toList();
    List<Demand> demands =
        assignments.stream()
            .map(
                a ->
                    new Demand(
                        a.classroomId(),
                        a.teacherId(),
                        a.subjectId(),
                        a.weeklyHours() - placedHours(existing, a)))
            .toList();
    Plan plan = TimetablePlanner.plan(demands, existing, days, grid.slotCount(), grid.lunchSlot());

    List<DraftLesson> lessons =
        plan.lessons().stream()
            .map(
                l ->
                    new DraftLesson(
                        UUID.randomUUID(),
                        l.classroomId(),
                        l.teacherId(),
                        l.subjectId(),
                        l.dayOfWeek(),
                        grid.startOf(l.span()),
                        grid.endOf(l.span())))
            .toList();
    List<MissingHours> missing =
        plan.missing().stream()
            .map(
                m ->
                    new MissingHours(
                        m.demand().classroomId(),
                        m.demand().teacherId(),
                        m.demand().subjectId(),
                        m.hours()))
            .toList();
    // Replacing the draft is serialized per branch: two admins generating at once would otherwise
    // both insert and one would hit the unique key.
    branchLock.lock(branch);
    saveDraft.deleteByBranchId(branch);
    TimetableDraft saved = saveDraft.save(TimetableDraft.create(branch, lessons, missing));
    log.atInfo()
        .addKeyValue("branchId", branch)
        .addKeyValue("draftId", saved.getId())
        .addKeyValue("lessons", lessons.size())
        .addKeyValue("unplacedDemands", missing.size())
        .log("Timetable draft generated");
    return view(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public TimetableDraftView current(Actor actor, UUID branchId) {
    return view(requireDraft(branch(actor, branchId, "timetable-draft.get")));
  }

  @Override
  @Transactional
  @Observed(name = "timetable-draft.move-lesson")
  public TimetableDraftView.Lesson move(Actor actor, MoveDraftLessonCommand command) {
    UUID branch = branch(actor, command.branchId(), "timetable-draft.move-lesson");
    TimetableDraft draft = requireDraft(branch);
    DraftLesson current = draft.requireLesson(command.lessonId());
    DraftLesson moved =
        new DraftLesson(
            current.id(),
            current.classroomId(),
            current.teacherId(),
            current.subjectId(),
            command.dayOfWeek(),
            command.startsAt(),
            command.endsAt());
    draft.requireValid(moved, gridOf(branch), existingLessons(branch));
    draft.replaceLesson(moved);
    saveDraft.save(draft);
    log.atInfo()
        .addKeyValue("draftId", draft.getId())
        .addKeyValue("lessonId", moved.id())
        .log("Draft lesson moved");
    return lessonView(moved);
  }

  @Override
  @Transactional
  @Observed(name = "timetable-draft.remove-lesson")
  public void remove(Actor actor, UUID branchId, UUID lessonId) {
    UUID branch = branch(actor, branchId, "timetable-draft.remove-lesson");
    TimetableDraft draft = requireDraft(branch);
    draft.removeLesson(lessonId);
    saveDraft.save(draft);
    log.atInfo()
        .addKeyValue("draftId", draft.getId())
        .addKeyValue("lessonId", lessonId)
        .log("Draft lesson removed");
  }

  @Override
  @Transactional
  @Observed(name = "timetable-draft.discard")
  public void discard(Actor actor, UUID branchId) {
    UUID branch = branch(actor, branchId, "timetable-draft.discard");
    saveDraft.deleteByBranchId(branch);
    log.atInfo().addKeyValue("branchId", branch).log("Timetable draft discarded");
  }

  /**
   * One transaction: the schedules are opened through the ordinary create use case (so its rules
   * apply) and any failure rolls all of them back, draft included. The user service is asked first,
   * before the branch lock and any write, so a slow call never holds the branch; the draft must
   * still match its assignments (a draft is kept until saved, and assignments can change
   * meanwhile). shortcut: the transaction (and its connection) is already open during that call;
   * split the method with a TransactionTemplate if the pool ever runs short.
   */
  @Override
  @Transactional
  @Observed(name = "timetable-draft.commit")
  public int commit(Actor actor, UUID branchId, LocalDate validFrom) {
    UUID branch = branch(actor, branchId, "timetable-draft.commit");
    List<BranchAssignment> assignments = userDirectory.loadBranchAssignments(branch);
    branchLock.lock(branch);
    TimetableDraft draft = requireDraft(branch);
    requireCurrent(draft, assignments);
    TimetableGrid grid = gridOf(branch);
    List<ScheduleConflicts.Lesson> existing = existingLessonsWithValidity(branch);
    for (DraftLesson lesson : draft.getLessons()) {
      draft.requireValid(lesson, grid, existing, validFrom);
    }
    for (DraftLesson lesson : draft.getLessons()) {
      createSchedule.create(
          actor,
          new CreateScheduleCommand(
              branch,
              lesson.classroomId(),
              lesson.teacherId(),
              lesson.subjectId(),
              lesson.dayOfWeek(),
              lesson.startsAt(),
              lesson.endsAt(),
              validFrom,
              null));
    }
    saveDraft.deleteByBranchId(branch);
    log.atInfo()
        .addKeyValue("branchId", branch)
        .addKeyValue("draftId", draft.getId())
        .addKeyValue("created", draft.getLessons().size())
        .log("Timetable draft saved as class schedules");
    return draft.getLessons().size();
  }

  private UUID branch(Actor actor, UUID requested, String action) {
    return BranchResolution.resolve(branchAccess, actor, requested, action);
  }

  private TimetableGrid gridOf(UUID branch) {
    return TimetableGrid.of(
        loadSetting.findByBranchId(branch).orElseGet(() -> TimetableSetting.defaults(branch)));
  }

  private List<ScheduleView> activeSchedules(UUID branch) {
    return scheduleQuery.findViews(branch, null, null, true, Pageable.unpaged()).getContent();
  }

  /** Rejects a draft whose lessons name an assignment that no longer exists (or a closed class). */
  private void requireCurrent(TimetableDraft draft, List<BranchAssignment> assignments) {
    Set<List<UUID>> current = new HashSet<>();
    assignments.forEach(a -> current.add(List.of(a.classroomId(), a.teacherId(), a.subjectId())));
    for (DraftLesson lesson : draft.getLessons()) {
      if (!current.contains(
          List.of(lesson.classroomId(), lesson.teacherId(), lesson.subjectId()))) {
        throw Rejections.reject(
            log,
            ErrorCode.TIMETABLE_DRAFT_STALE,
            "Draft rejected: a lesson names an assignment that no longer exists",
            "draftId",
            draft.getId(),
            "lessonId",
            lesson.id(),
            "classroomId",
            lesson.classroomId(),
            "teacherId",
            lesson.teacherId());
      }
    }
  }

  private List<ScheduleConflicts.Lesson> existingLessonsWithValidity(UUID branch) {
    return activeSchedules(branch).stream()
        .map(
            s ->
                new ScheduleConflicts.Lesson(
                    s.id(),
                    s.classroomId(),
                    s.teacherId(),
                    s.dayOfWeek(),
                    s.startsAt(),
                    s.endsAt(),
                    s.validFrom(),
                    s.validTo()))
        .toList();
  }

  private List<ScheduleConflicts.Lesson> existingLessons(UUID branch) {
    return activeSchedules(branch).stream()
        .map(
            s ->
                new ScheduleConflicts.Lesson(
                    s.id(),
                    s.classroomId(),
                    s.teacherId(),
                    s.dayOfWeek(),
                    s.startsAt(),
                    s.endsAt()))
        .toList();
  }

  private TimetableDraft requireDraft(UUID branch) {
    return loadDraft
        .findByBranchId(branch)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.TIMETABLE_DRAFT_NOT_FOUND,
                    "No draft timetable for the branch",
                    "branchId",
                    branch));
  }

  /** Hours of the assignment that already stand in the timetable. */
  private static int placedHours(List<Placed> existing, BranchAssignment a) {
    return existing.stream()
        .filter(
            l ->
                l.classroomId().equals(a.classroomId())
                    && l.teacherId().equals(a.teacherId())
                    && l.subjectId().equals(a.subjectId()))
        .mapToInt(l -> l.span().size())
        .sum();
  }

  private static TimetableDraftView view(TimetableDraft draft) {
    return new TimetableDraftView(
        draft.getId(),
        draft.getBranchId(),
        draft.getCreatedAt(),
        draft.getLessons().stream().map(TimetableDraftService::lessonView).toList(),
        draft.getMissing().stream()
            .map(
                m ->
                    new TimetableDraftView.Missing(
                        m.classroomId(), m.teacherId(), m.subjectId(), m.hours()))
            .toList());
  }

  private static TimetableDraftView.Lesson lessonView(DraftLesson l) {
    return new TimetableDraftView.Lesson(
        l.id(),
        l.classroomId(),
        l.teacherId(),
        l.subjectId(),
        l.dayOfWeek(),
        l.startsAt(),
        l.endsAt());
  }
}
