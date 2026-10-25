package com.dershane.userservice.user.application.service;

import static com.dershane.userservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.application.event.IntegrationEvent.GuardianLinked;
import com.dershane.userservice.shared.application.event.IntegrationEvent.GuardianUnlinked;
import com.dershane.userservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.user.application.port.in.LinkGuardianCommand;
import com.dershane.userservice.user.application.port.in.StudentGuardianView;
import com.dershane.userservice.user.application.port.out.LoadStudentGuardianPort;
import com.dershane.userservice.user.application.port.out.LoadStudentPort;
import com.dershane.userservice.user.application.port.out.LoadUserPort;
import com.dershane.userservice.user.application.port.out.SaveStudentGuardianPort;
import com.dershane.userservice.user.application.port.out.StudentGuardianQueryPort;
import com.dershane.userservice.user.domain.Guardian;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Relationship;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.StudentGuardian;
import com.dershane.userservice.user.domain.TargetExam;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentGuardianServiceTest {

  @Mock private LoadStudentGuardianPort loadLink;
  @Mock private SaveStudentGuardianPort saveLink;
  @Mock private StudentGuardianQueryPort linkQuery;
  @Mock private LoadUserPort users;
  @Mock private LoadStudentPort students;
  @Mock private IntegrationEventPublisher events;

  private final UUID branchId = UUID.randomUUID();
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");
  private StudentGuardianService service;
  private Student student;

  @BeforeEach
  void setUp() {
    service =
        new StudentGuardianService(
            loadLink,
            saveLink,
            linkQuery,
            new UserLookup(users, students),
            events,
            new BranchAccessPolicy(),
            mock(UserAccessPolicy.class));
    student =
        Student.register(
            UUID.randomUUID(),
            branchId,
            new PersonalInfo("Ali", "Kaya", null, null),
            "mrkz-1042",
            "1042",
            12,
            TargetExam.YKS);
    when(students.findByIdForUpdate(student.getId())).thenReturn(Optional.of(student));
  }

  @Test
  void linkingAnotherGuardianReplacesTheOldLinkInOrder() {
    Guardian newGuardian = guardian(branchId);
    StudentGuardian oldLink =
        new StudentGuardian(0L, UUID.randomUUID(), student.getId(), Relationship.FATHER);
    when(loadLink.findByStudentId(student.getId())).thenReturn(Optional.of(oldLink));
    when(linkQuery.findGuardianOfStudent(student.getId())).thenReturn(Optional.of(response()));

    service.link(
        admin, student.getId(), new LinkGuardianCommand(newGuardian.getId(), Relationship.MOTHER));

    InOrder order = inOrder(saveLink, events);
    order.verify(saveLink).deleteAndFlush(oldLink);
    order.verify(events).publish(any(GuardianUnlinked.class));
    order.verify(saveLink).save(any(StudentGuardian.class));
    order.verify(events).publish(any(GuardianLinked.class));
  }

  @Test
  void linkingTheFirstGuardianPublishesGuardianLinked() {
    Guardian guardian = guardian(branchId);
    when(loadLink.findByStudentId(student.getId())).thenReturn(Optional.empty());
    when(linkQuery.findGuardianOfStudent(student.getId())).thenReturn(Optional.of(response()));

    service.link(
        admin, student.getId(), new LinkGuardianCommand(guardian.getId(), Relationship.MOTHER));

    verify(saveLink).save(any(StudentGuardian.class));
    verify(events).publish(any(GuardianLinked.class));
    verify(saveLink, never()).deleteAndFlush(any());
  }

  @Test
  void relinkingSameGuardianWithSameRelationshipChangesNothing() {
    Guardian guardian = guardian(branchId);
    StudentGuardian link =
        new StudentGuardian(0L, guardian.getId(), student.getId(), Relationship.MOTHER);
    when(loadLink.findByStudentId(student.getId())).thenReturn(Optional.of(link));
    when(linkQuery.findGuardianOfStudent(student.getId())).thenReturn(Optional.of(response()));

    service.link(
        admin, student.getId(), new LinkGuardianCommand(guardian.getId(), Relationship.MOTHER));

    verify(saveLink, never()).save(any());
    verify(saveLink, never()).deleteAndFlush(any());
    verifyNoInteractions(events);
  }

  @Test
  void changingRelationshipOfSameGuardianPublishesGuardianLinked() {
    Guardian guardian = guardian(branchId);
    StudentGuardian link =
        new StudentGuardian(0L, guardian.getId(), student.getId(), Relationship.OTHER);
    when(loadLink.findByStudentId(student.getId())).thenReturn(Optional.of(link));
    when(linkQuery.findGuardianOfStudent(student.getId())).thenReturn(Optional.of(response()));

    service.link(
        admin,
        student.getId(),
        new LinkGuardianCommand(guardian.getId(), Relationship.LEGAL_GUARDIAN));

    verify(saveLink).save(link);
    verify(events).publish(any(GuardianLinked.class));
    verify(saveLink, never()).deleteAndFlush(any());
  }

  @Test
  void guardianOfAnotherBranchIsRejected() {
    Guardian guardian = guardian(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                service.link(
                    admin,
                    student.getId(),
                    new LinkGuardianCommand(guardian.getId(), Relationship.MOTHER)))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_MISMATCH);
    verifyNoInteractions(events);
  }

  @Test
  void adminOfAnotherBranchCannotLink() {
    Actor stranger = actor(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

    assertThatThrownBy(
            () ->
                service.link(
                    stranger,
                    student.getId(),
                    new LinkGuardianCommand(UUID.randomUUID(), Relationship.MOTHER)))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
    verifyNoInteractions(events);
  }

  @Test
  void unlinkingRemovesTheLinkAndPublishesGuardianUnlinked() {
    StudentGuardian link =
        new StudentGuardian(0L, UUID.randomUUID(), student.getId(), Relationship.MOTHER);
    when(loadLink.findByStudentId(student.getId())).thenReturn(Optional.of(link));

    service.unlink(admin, student.getId());

    verify(saveLink).deleteAndFlush(link);
    verify(events).publish(any(GuardianUnlinked.class));
  }

  @Test
  void unlinkingAStudentWithoutGuardianIsRejected() {
    when(loadLink.findByStudentId(student.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.unlink(admin, student.getId()))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.STUDENT_GUARDIAN_NOT_FOUND);
  }

  private Guardian guardian(UUID guardianBranchId) {
    Guardian guardian =
        Guardian.register(
            UUID.randomUUID(),
            guardianBranchId,
            new PersonalInfo("Ayşe", "Kaya", null, "+905551112233"),
            "905551112233");
    when(users.findById(guardian.getId())).thenReturn(Optional.of(guardian));
    return guardian;
  }

  private StudentGuardianView response() {
    return new StudentGuardianView(
        UUID.randomUUID(), student.getId(), "Ayşe", "Kaya", null, null, Relationship.MOTHER);
  }
}
