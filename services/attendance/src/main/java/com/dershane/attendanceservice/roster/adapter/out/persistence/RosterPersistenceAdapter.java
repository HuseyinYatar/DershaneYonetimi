package com.dershane.attendanceservice.roster.adapter.out.persistence;

import com.dershane.attendanceservice.roster.application.port.in.RosterEntryView;
import com.dershane.attendanceservice.roster.application.port.out.LoadGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.LoadMembershipReplicaPort;
import com.dershane.attendanceservice.roster.application.port.out.RosterQueryPort;
import com.dershane.attendanceservice.roster.application.port.out.SaveGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.SaveMembershipReplicaPort;
import com.dershane.attendanceservice.roster.domain.GuardianLinkReplica;
import com.dershane.attendanceservice.roster.domain.MembershipReplica;
import com.dershane.attendanceservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class RosterPersistenceAdapter
    implements LoadMembershipReplicaPort,
        SaveMembershipReplicaPort,
        RosterQueryPort,
        LoadGuardianLinkPort,
        SaveGuardianLinkPort {

  private final MembershipReplicaJpaRepository memberships;
  private final GuardianLinkReplicaJpaRepository links;
  private final RosterPersistenceMapper mapper;

  @Override
  public Optional<MembershipReplica> findById(UUID membershipId) {
    return memberships.findById(membershipId).map(mapper::toDomain);
  }

  @Override
  public Optional<UUID> findBranchIdOfStudent(UUID studentId) {
    return memberships
        .findFirstByStudentIdOrderByStartDateDesc(studentId)
        .map(MembershipReplicaJpaEntity::getBranchId);
  }

  @Override
  public void save(MembershipReplica membership) {
    if (membership.getVersion() == null) {
      memberships.save(mapper.toEntity(membership));
      return;
    }
    MembershipReplicaJpaEntity entity =
        memberships
            .findById(membership.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.MEMBERSHIP_NOT_FOUND,
                        "Membership to save no longer exists",
                        "membershipId",
                        membership.getId()));
    VersionChecks.requireCurrent(
        log, "MembershipReplica", membership.getId(), membership.getVersion(), entity.getVersion());
    mapper.update(entity, membership);
  }

  @Override
  public List<RosterEntryView> findRosterOn(UUID classroomId, UUID branchId, LocalDate date) {
    // Two memberships of the same student can cover the day they rejoined; list them once.
    Map<UUID, RosterEntryView> byStudent = new LinkedHashMap<>();
    memberships
        .findRosterOn(classroomId, branchId, date)
        .forEach(e -> byStudent.putIfAbsent(e.studentId(), e));
    return List.copyOf(byStudent.values());
  }

  @Override
  public boolean existsMembershipOn(
      UUID classroomId, UUID branchId, UUID studentId, LocalDate date) {
    return memberships.existsMembershipOn(classroomId, branchId, studentId, date);
  }

  @Override
  public boolean existsMembershipInOtherBranch(UUID classroomId, UUID branchId) {
    return memberships.existsByClassroomIdAndBranchIdNot(classroomId, branchId);
  }

  @Override
  public Optional<GuardianLinkReplica> findByStudentId(UUID studentId) {
    return links.findById(studentId).map(mapper::toDomain);
  }

  @Override
  public boolean existsByGuardianIdAndStudentId(UUID guardianId, UUID studentId) {
    return links.existsByGuardianIdAndStudentId(guardianId, studentId);
  }

  @Override
  public void save(GuardianLinkReplica link) {
    if (link.getVersion() == null) {
      links.save(mapper.toEntity(link));
      return;
    }
    GuardianLinkReplicaJpaEntity entity =
        links
            .findById(link.getStudentId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.DATA_CONFLICT,
                        "Guardian link to save no longer exists",
                        "studentId",
                        link.getStudentId()));
    VersionChecks.requireCurrent(
        log, "GuardianLinkReplica", link.getStudentId(), link.getVersion(), entity.getVersion());
    mapper.update(entity, link);
  }

  @Override
  public void deleteByStudentId(UUID studentId) {
    links.deleteById(studentId);
  }
}
