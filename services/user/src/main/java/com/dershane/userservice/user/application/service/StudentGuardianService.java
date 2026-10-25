package com.dershane.userservice.user.application.service;

import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.application.event.IntegrationEvent.GuardianLinked;
import com.dershane.userservice.shared.application.event.IntegrationEvent.GuardianUnlinked;
import com.dershane.userservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.in.GetStudentGuardianQuery;
import com.dershane.userservice.user.application.port.in.GuardianChildView;
import com.dershane.userservice.user.application.port.in.LinkGuardianCommand;
import com.dershane.userservice.user.application.port.in.LinkGuardianUseCase;
import com.dershane.userservice.user.application.port.in.ListGuardianChildrenQuery;
import com.dershane.userservice.user.application.port.in.StudentGuardianView;
import com.dershane.userservice.user.application.port.in.UnlinkGuardianUseCase;
import com.dershane.userservice.user.application.port.out.LoadStudentGuardianPort;
import com.dershane.userservice.user.application.port.out.SaveStudentGuardianPort;
import com.dershane.userservice.user.application.port.out.StudentGuardianQueryPort;
import com.dershane.userservice.user.domain.Guardian;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.StudentGuardian;
import com.dershane.userservice.user.domain.StudentGuardianLink;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student–guardian link. A student has at most one guardian: linking a different guardian replaces
 * the old link ({@code GuardianUnlinked} then {@code GuardianLinked}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class StudentGuardianService
    implements LinkGuardianUseCase,
        UnlinkGuardianUseCase,
        GetStudentGuardianQuery,
        ListGuardianChildrenQuery {

  private static final String LINK = "student.guardian.link";
  private static final String UNLINK = "student.guardian.unlink";

  private final LoadStudentGuardianPort loadLink;
  private final SaveStudentGuardianPort saveLink;
  private final StudentGuardianQueryPort linkQuery;
  private final UserLookup userLookup;
  private final IntegrationEventPublisher events;
  private final BranchAccessPolicy branchAccess;
  private final UserAccessPolicy userAccess;

  @Override
  @Transactional
  @Observed(name = LINK)
  public StudentGuardianView link(Actor actor, UUID studentId, LinkGuardianCommand command) {
    Student student = userLookup.lockStudent(studentId);
    branchAccess.requireBranchAdmin(actor, student.getBranchId(), LINK);
    Guardian guardian = userLookup.requireGuardian(command.guardianId());
    StudentGuardianLink.requireLinkable(student, guardian, LINK);

    Optional<StudentGuardian> current = loadLink.findByStudentId(studentId);
    StudentGuardianLink.Change change =
        StudentGuardianLink.replaceWith(
            current, studentId, guardian.getId(), command.relationship());
    switch (change) {
      case StudentGuardianLink.Created created -> linkNew(created.link());
      case StudentGuardianLink.Replaced replaced -> {
        unlinkReplaced(replaced.previous());
        linkNew(replaced.link());
      }
      case StudentGuardianLink.RelationshipChanged changed -> {
        saveLink.save(changed.link());
        publishLinked(changed.link());
        log.atInfo()
            .addKeyValue("studentId", studentId)
            .addKeyValue("guardianId", guardian.getId())
            .log("Guardian relationship updated");
      }
      case StudentGuardianLink.Unchanged unchanged ->
          log.atInfo()
              .addKeyValue("studentId", studentId)
              .addKeyValue("guardianId", guardian.getId())
              .log("Guardian already linked with the same relationship; no change");
    }
    return linkQuery
        .findGuardianOfStudent(studentId)
        .orElseThrow(() -> new IllegalStateException("Link just saved but not found"));
  }

  @Override
  @Transactional
  @Observed(name = UNLINK)
  public void unlink(Actor actor, UUID studentId) {
    Student student = userLookup.lockStudent(studentId);
    branchAccess.requireBranchAdmin(actor, student.getBranchId(), UNLINK);
    StudentGuardian link =
        loadLink
            .findByStudentId(studentId)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.STUDENT_GUARDIAN_NOT_FOUND,
                        "Unlink rejected: student has no guardian",
                        "studentId",
                        studentId));
    saveLink.deleteAndFlush(link);
    events.publish(new GuardianUnlinked(link.getGuardianId(), studentId));
    log.atInfo()
        .addKeyValue("studentId", studentId)
        .addKeyValue("guardianId", link.getGuardianId())
        .log("Guardian unlinked from student");
  }

  @Override
  @Transactional(readOnly = true)
  public StudentGuardianView guardianOf(Actor actor, UUID studentId) {
    Student student = userLookup.requireStudent(studentId);
    userAccess.requireCanView(actor, student);
    return linkQuery
        .findGuardianOfStudent(studentId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.STUDENT_GUARDIAN_NOT_FOUND,
                    "Student has no guardian",
                    "studentId",
                    studentId));
  }

  /** Children of a guardian: branch admins, or the guardian themself. */
  @Override
  @Transactional(readOnly = true)
  public List<GuardianChildView> childrenOf(Actor actor, UUID guardianId) {
    Guardian guardian = userLookup.requireGuardian(guardianId);
    if (actor.id().equals(guardianId)) {
      log.atDebug().addKeyValue("guardianId", guardianId).log("Guardian listing own children");
    } else {
      branchAccess.requireBranchAdmin(actor, guardian.getBranchId(), "guardian.children");
    }
    return linkQuery.findChildren(guardianId);
  }

  private void linkNew(StudentGuardian link) {
    saveLink.save(link);
    publishLinked(link);
    log.atInfo()
        .addKeyValue("studentId", link.getStudentId())
        .addKeyValue("guardianId", link.getGuardianId())
        .log("Guardian linked to student");
  }

  private void publishLinked(StudentGuardian link) {
    events.publish(
        new GuardianLinked(
            link.getGuardianId(), link.getStudentId(), link.getRelationship().name()));
  }

  /** The old link is deleted and flushed before the new one is inserted (see the port). */
  private void unlinkReplaced(StudentGuardian previous) {
    saveLink.deleteAndFlush(previous);
    events.publish(new GuardianUnlinked(previous.getGuardianId(), previous.getStudentId()));
    log.atInfo()
        .addKeyValue("studentId", previous.getStudentId())
        .addKeyValue("previousGuardianId", previous.getGuardianId())
        .log("Previous guardian unlinked (replaced)");
  }
}
