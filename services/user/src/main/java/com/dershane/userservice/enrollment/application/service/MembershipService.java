package com.dershane.userservice.enrollment.application.service;

import com.dershane.userservice.classroom.application.port.in.LoadClassroomQuery;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.enrollment.application.port.in.AssignClassroomCommand;
import com.dershane.userservice.enrollment.application.port.in.AssignStudentToClassroomUseCase;
import com.dershane.userservice.enrollment.application.port.in.CloseActiveMembershipUseCase;
import com.dershane.userservice.enrollment.application.port.in.MembershipView;
import com.dershane.userservice.enrollment.application.port.in.RemoveStudentFromClassroomUseCase;
import com.dershane.userservice.enrollment.application.port.out.LoadMembershipPort;
import com.dershane.userservice.enrollment.application.port.out.SaveMembershipPort;
import com.dershane.userservice.enrollment.domain.ClassroomMembership;
import com.dershane.userservice.enrollment.domain.EnrollmentPolicy;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.application.BusinessCalendar;
import com.dershane.userservice.shared.application.event.IntegrationEvent.StudentAddedToClassroom;
import com.dershane.userservice.shared.application.event.IntegrationEvent.StudentRemovedFromClassroom;
import com.dershane.userservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.in.LoadUserQuery;
import com.dershane.userservice.user.domain.Student;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrolls students in classrooms. A student has one active classroom; moving to another closes the
 * old membership and records {@code StudentRemovedFromClassroom} before {@code
 * StudentAddedToClassroom}, so consumers see the events in that order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class MembershipService
    implements AssignStudentToClassroomUseCase,
        RemoveStudentFromClassroomUseCase,
        CloseActiveMembershipUseCase {

  private static final String ASSIGN = "classroom.membership.assign";
  private static final String REMOVE = "classroom.membership.remove";

  private final LoadMembershipPort loadMembership;
  private final SaveMembershipPort saveMembership;
  private final LoadUserQuery userLookup;
  private final LoadClassroomQuery classroomLookup;
  private final IntegrationEventPublisher events;
  private final BusinessCalendar calendar;
  private final BranchAccessPolicy branchAccess;

  @Override
  @Transactional
  @Observed(name = ASSIGN)
  public MembershipView assign(Actor actor, UUID studentId, AssignClassroomCommand command) {
    Student student = userLookup.lockStudent(studentId);
    branchAccess.requireBranchAdmin(actor, student.getBranchId(), ASSIGN);
    Classroom classroom = classroomLookup.require(command.classroomId());
    EnrollmentPolicy.requireCanEnroll(student, classroom, ASSIGN);

    LocalDate startDate = command.startDate() == null ? calendar.today() : command.startDate();
    Optional<ClassroomMembership> current = loadMembership.findActiveByStudentId(studentId);
    if (current.isPresent() && current.get().getClassroomId().equals(classroom.getId())) {
      log.atInfo()
          .addKeyValue("studentId", studentId)
          .addKeyValue("classroomId", classroom.getId())
          .log("Student already in this classroom; no change");
      return view(current.get(), null);
    }

    UUID closedMembershipId = current.map(old -> close(old, student, startDate)).orElse(null);
    ClassroomMembership membership =
        saveMembership.save(ClassroomMembership.start(studentId, classroom.getId(), startDate));
    events.publish(
        new StudentAddedToClassroom(
            membership.getId(),
            studentId,
            classroom.getId(),
            student.getBranchId(),
            student.getStudentNumber(),
            student.fullName(),
            startDate));
    log.atInfo()
        .addKeyValue("studentId", studentId)
        .addKeyValue("classroomId", classroom.getId())
        .addKeyValue("membershipId", membership.getId())
        .addKeyValue("closedMembershipId", closedMembershipId)
        .log("Student added to classroom");
    return view(membership, closedMembershipId);
  }

  @Override
  @Transactional
  @Observed(name = REMOVE)
  public MembershipView remove(Actor actor, UUID studentId) {
    Student student = userLookup.lockStudent(studentId);
    branchAccess.requireBranchAdmin(actor, student.getBranchId(), REMOVE);
    ClassroomMembership membership =
        loadMembership
            .findActiveByStudentId(studentId)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND,
                        "Removal rejected: student has no active classroom",
                        "studentId",
                        studentId));
    close(membership, student, calendar.today());
    log.atInfo()
        .addKeyValue("studentId", studentId)
        .addKeyValue("membershipId", membership.getId())
        .log("Student removed from classroom");
    return view(membership, membership.getId());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<UUID> closeActiveMembership(UUID studentId, String action) {
    Optional<ClassroomMembership> active = loadMembership.findActiveByStudentId(studentId);
    if (active.isEmpty()) {
      log.atDebug()
          .addKeyValue("studentId", studentId)
          .addKeyValue("action", action)
          .log("Student has no active classroom; nothing to close");
      return Optional.empty();
    }
    Student student = userLookup.lockStudent(studentId);
    return Optional.of(close(active.get(), student, calendar.today()));
  }

  /**
   * Flushes before the caller inserts a new membership: Hibernate runs inserts before updates, and
   * the partial unique index allows only one open membership per student.
   */
  private UUID close(ClassroomMembership membership, Student student, LocalDate endDate) {
    membership.close(endDate);
    saveMembership.saveAndFlush(membership);
    events.publish(
        new StudentRemovedFromClassroom(
            membership.getId(),
            student.getId(),
            membership.getClassroomId(),
            student.getBranchId(),
            membership.getEndDate()));
    return membership.getId();
  }

  private static MembershipView view(ClassroomMembership membership, UUID closedMembershipId) {
    return new MembershipView(
        membership.getId(),
        membership.getStudentId(),
        membership.getClassroomId(),
        membership.getStartDate(),
        membership.getEndDate(),
        closedMembershipId);
  }
}
