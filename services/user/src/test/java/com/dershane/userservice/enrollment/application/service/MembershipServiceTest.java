package com.dershane.userservice.enrollment.application.service;

import static com.dershane.userservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dershane.userservice.classroom.application.port.in.LoadClassroomQuery;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.enrollment.application.port.in.AssignClassroomCommand;
import com.dershane.userservice.enrollment.application.port.in.MembershipView;
import com.dershane.userservice.enrollment.application.port.out.LoadMembershipPort;
import com.dershane.userservice.enrollment.application.port.out.SaveMembershipPort;
import com.dershane.userservice.enrollment.domain.ClassroomMembership;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.application.BusinessCalendar;
import com.dershane.userservice.shared.application.event.IntegrationEvent;
import com.dershane.userservice.shared.application.event.IntegrationEvent.StudentAddedToClassroom;
import com.dershane.userservice.shared.application.event.IntegrationEvent.StudentRemovedFromClassroom;
import com.dershane.userservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.user.application.port.in.LoadUserQuery;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.TargetExam;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  @Mock private LoadMembershipPort loadMembership;
  @Mock private SaveMembershipPort saveMembership;
  @Mock private LoadUserQuery userLookup;
  @Mock private LoadClassroomQuery classroomLookup;
  @Mock private IntegrationEventPublisher events;

  private final UUID branchId = UUID.randomUUID();
  private MembershipService service;
  private Student student;

  @BeforeEach
  void setUp() {
    BusinessCalendar calendar =
        new BusinessCalendar(
            Clock.fixed(Instant.parse("2026-10-17T07:00:00Z"), ZoneOffset.UTC), "Europe/Istanbul");
    service =
        new MembershipService(
            loadMembership,
            saveMembership,
            userLookup,
            classroomLookup,
            events,
            calendar,
            new BranchAccessPolicy());
    student =
        Student.register(
            UUID.randomUUID(),
            branchId,
            new PersonalInfo("Ali", "Kaya", null, null),
            "mrkz-1042",
            "1042",
            12,
            TargetExam.YKS);
    lenient().when(userLookup.lockStudent(student.getId())).thenReturn(student);
    lenient()
        .when(saveMembership.save(any(ClassroomMembership.class)))
        .thenAnswer(invocation -> saved(invocation.getArgument(0)));
  }

  @Test
  void movingStudentClosesOldMembershipBeforeOpeningTheNewOne() {
    Classroom target = classroom(12, branchId);
    ClassroomMembership old = membership(UUID.randomUUID(), LocalDate.of(2026, 9, 1));
    when(loadMembership.findActiveByStudentId(student.getId())).thenReturn(Optional.of(old));

    MembershipView response =
        service.assign(admin(), student.getId(), new AssignClassroomCommand(target.getId(), null));

    assertThat(old.getEndDate()).isEqualTo(TODAY);
    assertThat(response.closedMembershipId()).isEqualTo(old.getId());
    assertThat(response.classroomId()).isEqualTo(target.getId());
    assertThat(response.startDate()).isEqualTo(TODAY);

    InOrder order = inOrder(saveMembership, events);
    order.verify(saveMembership).saveAndFlush(old);
    order.verify(events).publish(any(StudentRemovedFromClassroom.class));
    order.verify(saveMembership).save(any(ClassroomMembership.class));
    order.verify(events).publish(any(StudentAddedToClassroom.class));
  }

  @Test
  void addedEventCarriesStudentDataForConsumerCopies() {
    Classroom target = classroom(12, branchId);
    when(loadMembership.findActiveByStudentId(student.getId())).thenReturn(Optional.empty());

    service.assign(admin(), student.getId(), new AssignClassroomCommand(target.getId(), null));

    ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
    verify(events, times(1)).publish(captor.capture());
    StudentAddedToClassroom added = (StudentAddedToClassroom) captor.getValue();
    assertThat(added.studentNumber()).isEqualTo("1042");
    assertThat(added.fullName()).isEqualTo("Ali Kaya");
    assertThat(added.branchId()).isEqualTo(branchId);
    assertThat(added.classroomId()).isEqualTo(target.getId());
  }

  @Test
  void assigningTheSameClassroomAgainChangesNothing() {
    Classroom target = classroom(12, branchId);
    ClassroomMembership current = membership(target.getId(), LocalDate.of(2026, 9, 1));
    when(loadMembership.findActiveByStudentId(student.getId())).thenReturn(Optional.of(current));

    MembershipView response =
        service.assign(admin(), student.getId(), new AssignClassroomCommand(target.getId(), null));

    assertThat(response.membershipId()).isEqualTo(current.getId());
    assertThat(response.closedMembershipId()).isNull();
    verify(saveMembership, never()).save(any());
    verifyNoInteractions(events);
  }

  @Test
  void rejectsClassroomOfAnotherGradeLevel() {
    assertRejected(classroom(11, branchId), ErrorCode.GRADE_LEVEL_MISMATCH);
  }

  @Test
  void rejectsClassroomOfAnotherBranch() {
    assertRejected(classroom(12, UUID.randomUUID()), ErrorCode.BRANCH_MISMATCH);
  }

  @Test
  void rejectsInactiveStudent() {
    student.deactivate();

    assertRejected(classroom(12, branchId), ErrorCode.USER_INACTIVE);
  }

  @Test
  void rejectsAdminOfAnotherBranch() {
    Classroom target = classroom(12, branchId);

    assertThatThrownBy(
            () ->
                service.assign(
                    actor(UUID.randomUUID(), UUID.randomUUID(), "ADMIN"),
                    student.getId(),
                    new AssignClassroomCommand(target.getId(), null)))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
    verifyNoInteractions(events);
  }

  @Test
  void removingStudentWithoutClassroomIsRejected() {
    when(loadMembership.findActiveByStudentId(student.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remove(admin(), student.getId()))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND);
  }

  @Test
  void removingStudentClosesTheMembershipAndRecordsTheEvent() {
    ClassroomMembership current = membership(UUID.randomUUID(), LocalDate.of(2026, 9, 1));
    when(loadMembership.findActiveByStudentId(student.getId())).thenReturn(Optional.of(current));

    MembershipView response = service.remove(admin(), student.getId());

    assertThat(response.endDate()).isEqualTo(TODAY);
    assertThat(response.closedMembershipId()).isEqualTo(current.getId());
    verify(saveMembership).saveAndFlush(current);
    verify(events).publish(any(StudentRemovedFromClassroom.class));
  }

  @Test
  void deactivatedStudentsMembershipIsClosedWithAnEvent() {
    ClassroomMembership current = membership(UUID.randomUUID(), LocalDate.of(2026, 9, 1));
    when(loadMembership.findActiveByStudentId(student.getId())).thenReturn(Optional.of(current));

    Optional<UUID> closed = service.closeActiveMembership(student.getId(), "user.deactivate");

    assertThat(closed).contains(current.getId());
    assertThat(current.getEndDate()).isEqualTo(TODAY);
    verify(events).publish(any(StudentRemovedFromClassroom.class));
  }

  @Test
  void studentWithoutClassroomHasNothingToClose() {
    when(loadMembership.findActiveByStudentId(student.getId())).thenReturn(Optional.empty());

    assertThat(service.closeActiveMembership(student.getId(), "user.deactivate")).isEmpty();
    verifyNoInteractions(events);
  }

  private void assertRejected(Classroom target, ErrorCode expected) {
    assertThatThrownBy(
            () ->
                service.assign(
                    admin(), student.getId(), new AssignClassroomCommand(target.getId(), null)))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(expected);
    verify(saveMembership, never()).save(any());
    verifyNoInteractions(events);
  }

  private com.dershane.userservice.shared.application.Actor admin() {
    return actor(UUID.randomUUID(), branchId, "ADMIN");
  }

  private Classroom classroom(int gradeLevel, UUID classroomBranchId) {
    Classroom classroom =
        new Classroom(
            UUID.randomUUID(), 0L, classroomBranchId, "12-A", gradeLevel, "2026-2027", true);
    lenient().when(classroomLookup.require(classroom.getId())).thenReturn(classroom);
    return classroom;
  }

  private ClassroomMembership membership(UUID classroomId, LocalDate startDate) {
    return new ClassroomMembership(
        UUID.randomUUID(), 0L, student.getId(), classroomId, startDate, null);
  }

  private static ClassroomMembership saved(ClassroomMembership membership) {
    return new ClassroomMembership(
        UUID.randomUUID(),
        0L,
        membership.getStudentId(),
        membership.getClassroomId(),
        membership.getStartDate(),
        membership.getEndDate());
  }
}
