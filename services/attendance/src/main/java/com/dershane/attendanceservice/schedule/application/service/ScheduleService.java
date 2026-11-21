package com.dershane.attendanceservice.schedule.application.service;

import com.dershane.attendanceservice.roster.application.port.in.ClassroomBranchQuery;
import com.dershane.attendanceservice.schedule.application.port.in.CreateScheduleCommand;
import com.dershane.attendanceservice.schedule.application.port.in.CreateScheduleUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.DeactivateScheduleUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateSessionsResult;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateSessionsUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GetScheduleQuery;
import com.dershane.attendanceservice.schedule.application.port.in.ListSchedulesQuery;
import com.dershane.attendanceservice.schedule.application.port.in.ScheduleView;
import com.dershane.attendanceservice.schedule.application.port.in.UpdateScheduleCommand;
import com.dershane.attendanceservice.schedule.application.port.in.UpdateScheduleUseCase;
import com.dershane.attendanceservice.schedule.application.port.out.BranchScheduleLockPort;
import com.dershane.attendanceservice.schedule.application.port.out.LoadSchedulePort;
import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableSettingPort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveSchedulePort;
import com.dershane.attendanceservice.schedule.application.port.out.ScheduleQueryPort;
import com.dershane.attendanceservice.schedule.domain.ClassSchedule;
import com.dershane.attendanceservice.schedule.domain.ScheduleConflicts;
import com.dershane.attendanceservice.schedule.domain.TimetableGrid;
import com.dershane.attendanceservice.schedule.domain.TimetableSetting;
import com.dershane.attendanceservice.session.application.port.in.CreateScheduledSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.ScheduledSessionCommand;
import com.dershane.attendanceservice.session.application.port.in.ScheduledSessionOutcome;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class ScheduleService
    implements CreateScheduleUseCase,
        UpdateScheduleUseCase,
        DeactivateScheduleUseCase,
        GetScheduleQuery,
        ListSchedulesQuery,
        GenerateSessionsUseCase {

  private final LoadSchedulePort loadSchedule;
  private final SaveSchedulePort saveSchedule;
  private final ScheduleQueryPort scheduleQuery;
  private final CreateScheduledSessionUseCase createSession;
  private final BranchAccessPolicy branchAccess;
  private final ClassroomBranchQuery classrooms;
  private final BusinessCalendar calendar;
  private final LoadTimetableSettingPort loadSetting;
  private final BranchScheduleLockPort branchLock;

  @Override
  @Transactional
  @Observed(name = "schedule.create")
  public ScheduleView create(Actor actor, CreateScheduleCommand command) {
    branchAccess.requireBranchAdmin(actor, command.branchId(), "schedule.create");
    if (classrooms.isKnownInOtherBranch(command.classroomId(), command.branchId())) {
      throw Rejections.reject(
          log,
          ErrorCode.BRANCH_MISMATCH,
          "Schedule rejected: the classroom belongs to another branch",
          "classroomId",
          command.classroomId(),
          "branchId",
          command.branchId());
    }
    ClassSchedule schedule =
        ClassSchedule.create(
            command.branchId(),
            command.classroomId(),
            command.teacherId(),
            command.subjectId(),
            command.dayOfWeek(),
            command.startsAt(),
            command.endsAt(),
            command.validFrom(),
            command.validTo());
    requireFree(schedule);
    ClassSchedule saved = saveSchedule.save(schedule);
    log.atInfo()
        .addKeyValue("scheduleId", saved.getId())
        .addKeyValue("classroomId", saved.getClassroomId())
        .log("Class schedule created");
    return requireView(saved.getId());
  }

  @Override
  @Transactional
  @Observed(name = "schedule.update")
  public ScheduleView update(Actor actor, UUID scheduleId, UpdateScheduleCommand command) {
    ClassSchedule schedule = require(scheduleId);
    branchAccess.requireBranchAdmin(actor, schedule.getBranchId(), "schedule.update");
    schedule.revise(
        command.teacherId(),
        command.subjectId(),
        command.dayOfWeek(),
        command.startsAt(),
        command.endsAt(),
        command.validFrom(),
        command.validTo());
    requireFree(schedule);
    saveSchedule.save(schedule);
    log.atInfo().addKeyValue("scheduleId", scheduleId).log("Class schedule updated");
    return requireView(scheduleId);
  }

  @Override
  @Transactional
  @Observed(name = "schedule.deactivate")
  public ScheduleView deactivate(Actor actor, UUID scheduleId) {
    ClassSchedule schedule = require(scheduleId);
    branchAccess.requireBranchAdmin(actor, schedule.getBranchId(), "schedule.deactivate");
    if (!schedule.deactivate()) {
      log.atInfo()
          .addKeyValue("scheduleId", scheduleId)
          .log("Class schedule already inactive; no change");
      return requireView(scheduleId);
    }
    saveSchedule.save(schedule);
    log.atInfo().addKeyValue("scheduleId", scheduleId).log("Class schedule deactivated");
    return requireView(scheduleId);
  }

  @Override
  @Transactional(readOnly = true)
  public ScheduleView get(Actor actor, UUID scheduleId) {
    ClassSchedule schedule = require(scheduleId);
    branchAccess.requireBranchAdmin(actor, schedule.getBranchId(), "schedule.get");
    return requireView(scheduleId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ScheduleView> list(
      Actor actor,
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      Boolean active,
      Pageable pageable) {
    UUID branchFilter = branchAccess.resolveBranchFilter(actor, branchId, "schedule.list");
    return scheduleQuery.findViews(branchFilter, classroomId, teacherId, active, pageable);
  }

  /**
   * Not transactional on purpose: each session is created in its own transaction, so one failing
   * day does not roll back the others.
   */
  @Override
  @Observed(name = "schedule.generate-sessions")
  public GenerateSessionsResult generate(int horizonDays) {
    LocalDate from = calendar.today();
    LocalDate to = from.plusDays(horizonDays);
    List<ClassSchedule> schedules = loadSchedule.findActiveValidBetween(from, to);
    if (schedules.isEmpty()) {
      log.atDebug()
          .addKeyValue("from", from)
          .addKeyValue("to", to)
          .log("No active schedules in range; nothing to generate");
      return new GenerateSessionsResult(0, 0, 0, 0);
    }
    int created = 0;
    int existing = 0;
    int skipped = 0;
    int failed = 0;
    for (ClassSchedule schedule : schedules) {
      for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
        if (!schedule.appliesOn(day)) {
          continue;
        }
        try {
          ScheduledSessionOutcome outcome =
              createSession.createFromSchedule(commandFor(schedule, day));
          switch (outcome) {
            case CREATED -> created++;
            case ALREADY_EXISTS -> existing++;
            default -> skipped++;
          }
        } catch (RuntimeException ex) {
          failed++;
          log.atError()
              .addKeyValue("scheduleId", schedule.getId())
              .addKeyValue("sessionDate", day)
              .setCause(ex)
              .log("Generating a session failed; continuing with the next day");
        }
      }
    }
    log.atInfo()
        .addKeyValue("created", created)
        .addKeyValue("alreadyExisting", existing)
        .addKeyValue("skippedConflicts", skipped)
        .addKeyValue("failed", failed)
        .log("Session generation finished");
    return new GenerateSessionsResult(created, existing, skipped, failed);
  }

  /**
   * The branch is locked first, so two admins cannot each pass this check and then both write. A
   * schedule never sits on the lunch hour and never shares a classroom or a teacher with another
   * active schedule of the branch whose validity range meets its own.
   */
  private void requireFree(ClassSchedule schedule) {
    UUID branch = schedule.getBranchId();
    branchLock.lock(branch);
    TimetableGrid grid =
        TimetableGrid.of(
            loadSetting.findByBranchId(branch).orElseGet(() -> TimetableSetting.defaults(branch)));
    if (grid.overlapsLunch(schedule.getStartsAt(), schedule.getEndsAt())) {
      throw Rejections.reject(
          log,
          ErrorCode.LUNCH_BREAK_SLOT,
          "Schedule rejected: it falls on the lunch hour",
          "branchId",
          branch,
          "classroomId",
          schedule.getClassroomId());
    }
    List<ScheduleConflicts.Lesson> others =
        scheduleQuery.findViews(branch, null, null, true, Pageable.unpaged()).stream()
            .map(ScheduleService::asLesson)
            .toList();
    ScheduleConflicts.find(asLesson(schedule), others)
        .ifPresent(
            kind -> {
              boolean classroom = kind == ScheduleConflicts.Kind.CLASSROOM;
              throw Rejections.reject(
                  log,
                  classroom ? ErrorCode.CLASSROOM_BUSY : ErrorCode.TEACHER_BUSY,
                  classroom
                      ? "Schedule rejected: the classroom has a lesson then"
                      : "Schedule rejected: the teacher has a lesson then",
                  "branchId",
                  branch,
                  "classroomId",
                  schedule.getClassroomId(),
                  "teacherId",
                  schedule.getTeacherId());
            });
  }

  private static ScheduleConflicts.Lesson asLesson(ClassSchedule s) {
    return new ScheduleConflicts.Lesson(
        s.getId(),
        s.getClassroomId(),
        s.getTeacherId(),
        s.getDayOfWeek(),
        s.getStartsAt(),
        s.getEndsAt(),
        s.getValidFrom(),
        s.getValidTo());
  }

  private static ScheduleConflicts.Lesson asLesson(ScheduleView v) {
    return new ScheduleConflicts.Lesson(
        v.id(),
        v.classroomId(),
        v.teacherId(),
        v.dayOfWeek(),
        v.startsAt(),
        v.endsAt(),
        v.validFrom(),
        v.validTo());
  }

  private static ScheduledSessionCommand commandFor(ClassSchedule schedule, LocalDate day) {
    return new ScheduledSessionCommand(
        schedule.getId(),
        schedule.getBranchId(),
        schedule.getClassroomId(),
        schedule.getTeacherId(),
        schedule.getSubjectId(),
        day,
        schedule.getStartsAt(),
        schedule.getEndsAt());
  }

  private ClassSchedule require(UUID scheduleId) {
    return loadSchedule
        .findById(scheduleId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.SCHEDULE_NOT_FOUND,
                    "Class schedule not found",
                    "scheduleId",
                    scheduleId));
  }

  private ScheduleView requireView(UUID scheduleId) {
    return scheduleQuery
        .findViewById(scheduleId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.SCHEDULE_NOT_FOUND,
                    "Class schedule not found",
                    "scheduleId",
                    scheduleId));
  }
}
