package com.dershane.userservice.classroom.application.service;

import com.dershane.userservice.branch.application.port.in.LoadBranchQuery;
import com.dershane.userservice.classroom.application.port.in.ClassroomView;
import com.dershane.userservice.classroom.application.port.in.CreateClassroomCommand;
import com.dershane.userservice.classroom.application.port.in.CreateClassroomUseCase;
import com.dershane.userservice.classroom.application.port.in.DeactivateClassroomUseCase;
import com.dershane.userservice.classroom.application.port.in.GetClassroomQuery;
import com.dershane.userservice.classroom.application.port.in.ListClassroomsQuery;
import com.dershane.userservice.classroom.application.port.in.UpdateClassroomCommand;
import com.dershane.userservice.classroom.application.port.in.UpdateClassroomUseCase;
import com.dershane.userservice.classroom.application.port.out.ClassroomEnrollmentPort;
import com.dershane.userservice.classroom.application.port.out.ClassroomQueryPort;
import com.dershane.userservice.classroom.application.port.out.LoadClassroomPort;
import com.dershane.userservice.classroom.application.port.out.SaveClassroomPort;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
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
class ClassroomService
    implements CreateClassroomUseCase,
        UpdateClassroomUseCase,
        DeactivateClassroomUseCase,
        GetClassroomQuery,
        ListClassroomsQuery {

  private final LoadClassroomPort loadClassroom;
  private final SaveClassroomPort saveClassroom;
  private final ClassroomQueryPort classroomQuery;
  private final ClassroomEnrollmentPort enrollment;
  private final ClassroomLookup classroomLookup;
  private final ClassroomAccessPolicy classroomAccess;
  private final LoadBranchQuery branchLookup;
  private final BranchAccessPolicy branchAccess;

  @Override
  @Transactional
  @Observed(name = "classroom.create")
  public ClassroomView create(Actor actor, CreateClassroomCommand command) {
    branchAccess.requireBranchAdmin(actor, command.branchId(), "classroom.create");
    branchLookup.requireActive(command.branchId(), "classroom.create");
    String name = command.name().strip();
    requireNameFree(command.branchId(), command.term(), name);
    Classroom classroom =
        saveClassroom.save(
            Classroom.create(command.branchId(), name, command.gradeLevel(), command.term()));
    log.atInfo()
        .addKeyValue("classroomId", classroom.getId())
        .addKeyValue("branchId", classroom.getBranchId())
        .log("Classroom created");
    return withCount(requireView(classroom.getId()), 0L);
  }

  @Override
  @Transactional
  @Observed(name = "classroom.update")
  public ClassroomView update(Actor actor, UUID classroomId, UpdateClassroomCommand command) {
    Classroom classroom = classroomLookup.require(classroomId);
    branchAccess.requireBranchAdmin(actor, classroom.getBranchId(), "classroom.update");
    String newName = command.name() == null ? classroom.getName() : command.name().strip();
    String newTerm = command.term() == null ? classroom.getTerm() : command.term();
    boolean renamed = !newName.equals(classroom.getName()) || !newTerm.equals(classroom.getTerm());
    if (renamed) {
      requireNameFree(classroom.getBranchId(), newTerm, newName);
    }
    classroom.update(newName, newTerm);
    saveClassroom.save(classroom);
    log.atInfo().addKeyValue("classroomId", classroomId).log("Classroom updated");
    return withCurrentCount(requireView(classroomId));
  }

  @Override
  @Transactional
  @Observed(name = "classroom.deactivate")
  public ClassroomView deactivate(Actor actor, UUID classroomId) {
    Classroom classroom = classroomLookup.require(classroomId);
    branchAccess.requireBranchAdmin(actor, classroom.getBranchId(), "classroom.deactivate");
    if (!classroom.deactivate(enrollment.hasActiveStudents(classroomId))) {
      log.atInfo()
          .addKeyValue("classroomId", classroomId)
          .log("Classroom already inactive; no change");
      return withCount(requireView(classroomId), 0L);
    }
    saveClassroom.save(classroom);
    log.atInfo().addKeyValue("classroomId", classroomId).log("Classroom deactivated");
    return withCount(requireView(classroomId), 0L);
  }

  @Override
  @Transactional(readOnly = true)
  public ClassroomView get(Actor actor, UUID classroomId) {
    Classroom classroom = classroomLookup.require(classroomId);
    classroomAccess.requireCanView(actor, classroom);
    return withCurrentCount(requireView(classroomId));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ClassroomView> list(
      Actor actor, UUID branchId, String term, Boolean active, Pageable pageable) {
    UUID branchFilter = branchAccess.resolveBranchFilter(actor, branchId, "classroom.list");
    return classroomQuery.findViews(branchFilter, term, active, pageable);
  }

  private ClassroomView withCurrentCount(ClassroomView view) {
    return withCount(view, enrollment.countActiveStudents(view.id()));
  }

  private static ClassroomView withCount(ClassroomView view, long count) {
    return new ClassroomView(
        view.id(),
        view.branchId(),
        view.name(),
        view.gradeLevel(),
        view.term(),
        view.active(),
        count,
        view.createdAt(),
        view.updatedAt());
  }

  private ClassroomView requireView(UUID classroomId) {
    return classroomQuery
        .findViewById(classroomId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.CLASSROOM_NOT_FOUND,
                    "Classroom not found",
                    "classroomId",
                    classroomId));
  }

  private void requireNameFree(UUID branchId, String term, String name) {
    if (loadClassroom.existsByBranchIdAndTermAndName(branchId, term, name)) {
      throw Rejections.reject(
          log,
          ErrorCode.CLASSROOM_NAME_TAKEN,
          "Classroom name already used in this branch and term",
          "branchId",
          branchId,
          "term",
          term);
    }
  }
}
