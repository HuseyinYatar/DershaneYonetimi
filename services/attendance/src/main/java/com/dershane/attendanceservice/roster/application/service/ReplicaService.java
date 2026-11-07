package com.dershane.attendanceservice.roster.application.service;

import com.dershane.attendanceservice.roster.application.port.in.LinkGuardianUseCase;
import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipAddedCommand;
import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipAddedUseCase;
import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipRemovedUseCase;
import com.dershane.attendanceservice.roster.application.port.in.UnlinkGuardianUseCase;
import com.dershane.attendanceservice.roster.application.port.out.LoadGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.LoadMembershipReplicaPort;
import com.dershane.attendanceservice.roster.application.port.out.SaveGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.SaveMembershipReplicaPort;
import com.dershane.attendanceservice.roster.domain.GuardianLinkReplica;
import com.dershane.attendanceservice.roster.domain.MembershipReplica;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the local copies of memberships and guardian links; every operation is idempotent. */
@Slf4j
@Service
@RequiredArgsConstructor
class ReplicaService
    implements RecordMembershipAddedUseCase,
        RecordMembershipRemovedUseCase,
        LinkGuardianUseCase,
        UnlinkGuardianUseCase {

  private final LoadMembershipReplicaPort loadMemberships;
  private final SaveMembershipReplicaPort saveMemberships;
  private final LoadGuardianLinkPort loadLinks;
  private final SaveGuardianLinkPort saveLinks;
  private final BranchAccessPolicy branchAccess;

  @Override
  @Transactional
  @Observed(name = "roster.membership.add")
  public void recordAdded(Actor actor, RecordMembershipAddedCommand command) {
    branchAccess.requireBranchAdmin(actor, command.branchId(), "roster.membership.add");
    if (loadMemberships.findById(command.membershipId()).isPresent()) {
      log.atDebug()
          .addKeyValue("membershipId", command.membershipId())
          .log("Membership already recorded; repeated event ignored");
      return;
    }
    saveMemberships.save(
        MembershipReplica.open(
            command.membershipId(),
            command.classroomId(),
            command.studentId(),
            command.branchId(),
            command.startDate(),
            command.studentNumber(),
            command.fullName()));
    log.atInfo()
        .addKeyValue("membershipId", command.membershipId())
        .addKeyValue("studentId", command.studentId())
        .addKeyValue("classroomId", command.classroomId())
        .log("Membership recorded");
  }

  @Override
  @Transactional
  @Observed(name = "roster.membership.remove")
  public void recordRemoved(Actor actor, UUID membershipId, LocalDate endDate) {
    MembershipReplica membership =
        loadMemberships
            .findById(membershipId)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.MEMBERSHIP_NOT_FOUND,
                        "Membership to close is unknown",
                        "membershipId",
                        membershipId));
    branchAccess.requireBranchAdmin(actor, membership.getBranchId(), "roster.membership.remove");
    if (!membership.close(endDate)) {
      log.atDebug()
          .addKeyValue("membershipId", membershipId)
          .log("Membership already closed on this date; repeated event ignored");
      return;
    }
    saveMemberships.save(membership);
    log.atInfo().addKeyValue("membershipId", membershipId).log("Membership closed");
  }

  @Override
  @Transactional
  @Observed(name = "roster.guardian.link")
  public void link(Actor actor, UUID guardianId, UUID studentId) {
    requireBranchAdminOfStudent(actor, studentId, "roster.guardian.link");
    Optional<GuardianLinkReplica> existing = loadLinks.findByStudentId(studentId);
    if (existing.isEmpty()) {
      saveLinks.save(GuardianLinkReplica.of(studentId, guardianId));
    } else if (existing.get().getGuardianId().equals(guardianId)) {
      log.atDebug()
          .addKeyValue("studentId", studentId)
          .log("Guardian already linked; repeated event ignored");
      return;
    } else {
      saveLinks.save(new GuardianLinkReplica(studentId, existing.get().getVersion(), guardianId));
    }
    log.atInfo()
        .addKeyValue("studentId", studentId)
        .addKeyValue("guardianId", guardianId)
        .log("Guardian link recorded");
  }

  @Override
  @Transactional
  @Observed(name = "roster.guardian.unlink")
  public void unlink(Actor actor, UUID studentId, UUID guardianId) {
    requireBranchAdminOfStudent(actor, studentId, "roster.guardian.unlink");
    Optional<GuardianLinkReplica> existing = loadLinks.findByStudentId(studentId);
    if (existing.isEmpty()) {
      log.atDebug()
          .addKeyValue("studentId", studentId)
          .log("Student has no guardian link; repeated event ignored");
      return;
    }
    if (!existing.get().getGuardianId().equals(guardianId)) {
      log.atInfo()
          .addKeyValue("studentId", studentId)
          .addKeyValue("guardianId", guardianId)
          .log("Student is linked to another guardian; stale unlink ignored");
      return;
    }
    saveLinks.deleteByStudentId(studentId);
    log.atInfo()
        .addKeyValue("studentId", studentId)
        .addKeyValue("guardianId", guardianId)
        .log("Guardian link removed");
  }

  /** A student's branch is known from their membership; without one only super admins may act. */
  private void requireBranchAdminOfStudent(Actor actor, UUID studentId, String action) {
    Optional<UUID> branchId = loadMemberships.findBranchIdOfStudent(studentId);
    if (branchId.isPresent()) {
      branchAccess.requireBranchAdmin(actor, branchId.get(), action);
      return;
    }
    if (!actor.isSuperAdmin()) {
      throw Rejections.reject(
          log,
          ErrorCode.BRANCH_SCOPE_VIOLATION,
          "Student has no membership, so only a super admin may change the guardian link",
          "callerId",
          actor.id(),
          "studentId",
          studentId,
          "action",
          action);
    }
  }
}
