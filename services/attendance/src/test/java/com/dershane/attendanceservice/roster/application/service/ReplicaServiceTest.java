package com.dershane.attendanceservice.roster.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipAddedCommand;
import com.dershane.attendanceservice.roster.application.port.out.LoadGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.LoadMembershipReplicaPort;
import com.dershane.attendanceservice.roster.application.port.out.SaveGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.SaveMembershipReplicaPort;
import com.dershane.attendanceservice.roster.domain.GuardianLinkReplica;
import com.dershane.attendanceservice.roster.domain.MembershipReplica;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplicaServiceTest {

  private static final LocalDate START = LocalDate.of(2026, 9, 14);

  @Mock private LoadMembershipReplicaPort loadMemberships;
  @Mock private SaveMembershipReplicaPort saveMemberships;
  @Mock private LoadGuardianLinkPort loadLinks;
  @Mock private SaveGuardianLinkPort saveLinks;

  private final UUID branchId = UUID.randomUUID();
  private final UUID membershipId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID guardianId = UUID.randomUUID();
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");

  private ReplicaService service;

  @BeforeEach
  void setUp() {
    service =
        new ReplicaService(
            loadMemberships, saveMemberships, loadLinks, saveLinks, new BranchAccessPolicy());
  }

  private RecordMembershipAddedCommand added() {
    return new RecordMembershipAddedCommand(
        membershipId, studentId, UUID.randomUUID(), branchId, "1042", "Ali Kaya", START);
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void aNewMembershipIsStoredOpen() {
    when(loadMemberships.findById(membershipId)).thenReturn(Optional.empty());

    service.recordAdded(admin, added());

    ArgumentCaptor<MembershipReplica> saved = ArgumentCaptor.forClass(MembershipReplica.class);
    verify(saveMemberships).save(saved.capture());
    assertThat(saved.getValue().getId()).isEqualTo(membershipId);
    assertThat(saved.getValue().getEndDate()).isNull();
  }

  @Test
  void aRepeatedMembershipEventChangesNothing() {
    when(loadMemberships.findById(membershipId)).thenReturn(Optional.of(open()));

    service.recordAdded(admin, added());

    verify(saveMemberships, never()).save(any());
  }

  @Test
  void anAdminOfAnotherBranchCannotRecordAMembership() {
    Actor otherAdmin = actor(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

    assertRejected(
        () -> service.recordAdded(otherAdmin, added()), ErrorCode.BRANCH_SCOPE_VIOLATION);
    verify(saveMemberships, never()).save(any());
  }

  @Test
  void closingSetsTheEndDateOnce() {
    MembershipReplica membership = open();
    when(loadMemberships.findById(membershipId)).thenReturn(Optional.of(membership));

    service.recordRemoved(admin, membershipId, START.plusDays(5));
    service.recordRemoved(admin, membershipId, START.plusDays(5));

    assertThat(membership.getEndDate()).isEqualTo(START.plusDays(5));
    verify(saveMemberships, org.mockito.Mockito.times(1)).save(membership);
  }

  @Test
  void closingAnUnknownMembershipIsNotFound() {
    when(loadMemberships.findById(membershipId)).thenReturn(Optional.empty());

    assertRejected(
        () -> service.recordRemoved(admin, membershipId, START), ErrorCode.MEMBERSHIP_NOT_FOUND);
  }

  @Test
  void aNewGuardianLinkIsStored() {
    when(loadMemberships.findBranchIdOfStudent(studentId)).thenReturn(Optional.of(branchId));
    when(loadLinks.findByStudentId(studentId)).thenReturn(Optional.empty());

    service.link(admin, guardianId, studentId);

    ArgumentCaptor<GuardianLinkReplica> saved = ArgumentCaptor.forClass(GuardianLinkReplica.class);
    verify(saveLinks).save(saved.capture());
    assertThat(saved.getValue().getGuardianId()).isEqualTo(guardianId);
    assertThat(saved.getValue().getVersion()).isNull();
  }

  @Test
  void anotherGuardianReplacesTheLinkKeepingItsVersion() {
    when(loadMemberships.findBranchIdOfStudent(studentId)).thenReturn(Optional.of(branchId));
    when(loadLinks.findByStudentId(studentId))
        .thenReturn(Optional.of(new GuardianLinkReplica(studentId, 4L, UUID.randomUUID())));

    service.link(admin, guardianId, studentId);

    ArgumentCaptor<GuardianLinkReplica> saved = ArgumentCaptor.forClass(GuardianLinkReplica.class);
    verify(saveLinks).save(saved.capture());
    assertThat(saved.getValue().getGuardianId()).isEqualTo(guardianId);
    assertThat(saved.getValue().getVersion()).isEqualTo(4L);
  }

  @Test
  void theSameGuardianLinkAgainChangesNothing() {
    when(loadMemberships.findBranchIdOfStudent(studentId)).thenReturn(Optional.of(branchId));
    when(loadLinks.findByStudentId(studentId))
        .thenReturn(Optional.of(new GuardianLinkReplica(studentId, 4L, guardianId)));

    service.link(admin, guardianId, studentId);

    verify(saveLinks, never()).save(any());
  }

  @Test
  void aStudentWithoutAMembershipCanOnlyBeLinkedBySuperAdmins() {
    when(loadMemberships.findBranchIdOfStudent(studentId)).thenReturn(Optional.empty());

    assertRejected(
        () -> service.link(admin, guardianId, studentId), ErrorCode.BRANCH_SCOPE_VIOLATION);

    Actor superAdmin = actor(UUID.randomUUID(), branchId, "ADMIN", "SUPER_ADMIN");
    when(loadLinks.findByStudentId(studentId)).thenReturn(Optional.empty());
    service.link(superAdmin, guardianId, studentId);
    verify(saveLinks).save(any());
  }

  @Test
  void unlinkingDeletesTheLinkOnlyWhenThereIsOne() {
    when(loadMemberships.findBranchIdOfStudent(studentId)).thenReturn(Optional.of(branchId));
    when(loadLinks.findByStudentId(studentId))
        .thenReturn(Optional.of(new GuardianLinkReplica(studentId, 1L, guardianId)))
        .thenReturn(Optional.empty());

    service.unlink(admin, studentId);
    service.unlink(admin, studentId);

    verify(saveLinks, org.mockito.Mockito.times(1)).deleteByStudentId(studentId);
  }

  private MembershipReplica open() {
    return new MembershipReplica(
        membershipId, 0L, UUID.randomUUID(), studentId, branchId, START, null, "1042", "Ali Kaya");
  }
}
