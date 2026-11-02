package com.dershane.attendanceservice.excuse.application.service;

import com.dershane.attendanceservice.excuse.application.event.ExcuseApproved;
import com.dershane.attendanceservice.excuse.application.port.in.DecideExcuseUseCase;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseCoverageQuery;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseView;
import com.dershane.attendanceservice.excuse.application.port.in.ListExcusesQuery;
import com.dershane.attendanceservice.excuse.application.port.in.RequestExcuseCommand;
import com.dershane.attendanceservice.excuse.application.port.in.RequestExcuseUseCase;
import com.dershane.attendanceservice.excuse.application.port.out.ExcuseQueryPort;
import com.dershane.attendanceservice.excuse.application.port.out.LoadExcusePort;
import com.dershane.attendanceservice.excuse.application.port.out.SaveExcusePort;
import com.dershane.attendanceservice.excuse.domain.Excuse;
import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import com.dershane.attendanceservice.roster.application.port.in.GuardianAccessQuery;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class ExcuseService
    implements RequestExcuseUseCase, DecideExcuseUseCase, ListExcusesQuery, ExcuseCoverageQuery {

  private final LoadExcusePort loadExcuse;
  private final SaveExcusePort saveExcuse;
  private final ExcuseQueryPort excuseQuery;
  private final GuardianAccessQuery guardians;
  private final BranchAccessPolicy branchAccess;
  private final BusinessCalendar calendar;
  private final ApplicationEventPublisher applicationEvents;

  @Override
  @Transactional
  @Observed(name = "excuse.request")
  public ExcuseView request(Actor actor, RequestExcuseCommand command) {
    if (!actor.isGuardian()) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Only a guardian can report an excuse",
          "callerId",
          actor.id());
    }
    if (!guardians.isGuardianOf(actor.id(), command.studentId())) {
      throw Rejections.reject(
          log,
          ErrorCode.GUARDIAN_NOT_LINKED,
          "Excuse rejected: the student is not linked to the caller",
          "callerId",
          actor.id(),
          "studentId",
          command.studentId());
    }
    if (actor.branchId() == null) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Excuse rejected: the guardian's token carries no branch",
          "callerId",
          actor.id());
    }
    Excuse saved =
        saveExcuse.save(
            Excuse.request(
                command.studentId(),
                actor.branchId(),
                command.fromDate(),
                command.toDate(),
                command.reason(),
                actor.id()));
    log.atInfo()
        .addKeyValue("excuseId", saved.getId())
        .addKeyValue("studentId", saved.getStudentId())
        .log("Excuse reported");
    return requireView(saved.getId());
  }

  @Override
  @Transactional
  @Observed(name = "excuse.approve")
  public ExcuseView approve(Actor actor, UUID excuseId, String note) {
    Excuse excuse = require(excuseId);
    branchAccess.requireBranchAdmin(actor, excuse.getBranchId(), "excuse.approve");
    excuse.approve(actor.id(), calendar.now(), note);
    saveExcuse.save(excuse);
    applicationEvents.publishEvent(
        new ExcuseApproved(
            excuseId, excuse.getStudentId(), excuse.getFromDate(), excuse.getToDate(), actor.id()));
    log.atInfo().addKeyValue("excuseId", excuseId).log("Excuse approved");
    return requireView(excuseId);
  }

  @Override
  @Transactional
  @Observed(name = "excuse.reject")
  public ExcuseView reject(Actor actor, UUID excuseId, String note) {
    Excuse excuse = require(excuseId);
    branchAccess.requireBranchAdmin(actor, excuse.getBranchId(), "excuse.reject");
    excuse.reject(actor.id(), calendar.now(), note);
    saveExcuse.save(excuse);
    log.atInfo().addKeyValue("excuseId", excuseId).log("Excuse rejected");
    return requireView(excuseId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ExcuseView> list(
      Actor actor, ExcuseStatus status, UUID studentId, Pageable pageable) {
    if (actor.isAdmin() || actor.isSuperAdmin()) {
      UUID branchFilter = branchAccess.resolveBranchFilter(actor, null, "excuse.list");
      return excuseQuery.findViews(branchFilter, null, studentId, status, pageable);
    }
    if (actor.isGuardian()) {
      return excuseQuery.findViews(null, actor.id(), studentId, status, pageable);
    }
    throw Rejections.reject(
        log,
        ErrorCode.ACCESS_DENIED,
        "Only admins and guardians list excuses",
        "callerId",
        actor.id());
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, UUID> coveredExcuses(Collection<UUID> studentIds, LocalDate date) {
    if (studentIds.isEmpty()) {
      return Map.of();
    }
    return loadExcuse.findApprovedCoverage(studentIds, date);
  }

  private Excuse require(UUID excuseId) {
    return loadExcuse
        .findById(excuseId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.EXCUSE_NOT_FOUND, "Excuse not found", "excuseId", excuseId));
  }

  private ExcuseView requireView(UUID excuseId) {
    return excuseQuery
        .findViewById(excuseId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.EXCUSE_NOT_FOUND, "Excuse not found", "excuseId", excuseId));
  }
}
