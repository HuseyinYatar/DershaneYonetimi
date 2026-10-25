package com.dershane.userservice.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.user.domain.StudentGuardianLink.Change;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentGuardianLinkTest {

  private final UUID branchId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID guardianId = UUID.randomUUID();

  @Test
  void firstGuardianCreatesALink() {
    Change change =
        StudentGuardianLink.replaceWith(
            Optional.empty(), studentId, guardianId, Relationship.MOTHER);

    assertThat(change).isInstanceOf(StudentGuardianLink.Created.class);
    StudentGuardian link = ((StudentGuardianLink.Created) change).link();
    assertThat(link.getGuardianId()).isEqualTo(guardianId);
    assertThat(link.getRelationship()).isEqualTo(Relationship.MOTHER);
  }

  @Test
  void anotherGuardianReplacesTheOldLink() {
    StudentGuardian previous =
        StudentGuardian.link(UUID.randomUUID(), studentId, Relationship.FATHER);

    Change change =
        StudentGuardianLink.replaceWith(
            Optional.of(previous), studentId, guardianId, Relationship.MOTHER);

    assertThat(change).isInstanceOf(StudentGuardianLink.Replaced.class);
    StudentGuardianLink.Replaced replaced = (StudentGuardianLink.Replaced) change;
    assertThat(replaced.previous()).isSameAs(previous);
    assertThat(replaced.link().getGuardianId()).isEqualTo(guardianId);
  }

  @Test
  void sameGuardianWithAnotherRelationshipUpdatesTheLink() {
    StudentGuardian existing = StudentGuardian.link(guardianId, studentId, Relationship.OTHER);

    Change change =
        StudentGuardianLink.replaceWith(
            Optional.of(existing), studentId, guardianId, Relationship.LEGAL_GUARDIAN);

    assertThat(change).isInstanceOf(StudentGuardianLink.RelationshipChanged.class);
    assertThat(existing.getRelationship()).isEqualTo(Relationship.LEGAL_GUARDIAN);
  }

  @Test
  void sameGuardianWithTheSameRelationshipChangesNothing() {
    StudentGuardian existing = StudentGuardian.link(guardianId, studentId, Relationship.MOTHER);

    Change change =
        StudentGuardianLink.replaceWith(
            Optional.of(existing), studentId, guardianId, Relationship.MOTHER);

    assertThat(change).isInstanceOf(StudentGuardianLink.Unchanged.class);
  }

  @Test
  void guardianAndStudentMustShareABranch() {
    Student student = student(branchId);
    Guardian guardian = guardian(UUID.randomUUID());

    assertThatThrownBy(() -> StudentGuardianLink.requireLinkable(student, guardian, "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_MISMATCH);
  }

  @Test
  void bothMustBeActive() {
    Student student = student(branchId);
    Guardian guardian = guardian(branchId);
    guardian.deactivate();

    assertThatThrownBy(() -> StudentGuardianLink.requireLinkable(student, guardian, "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_INACTIVE);
  }

  @Test
  void activeUsersOfTheSameBranchCanBeLinked() {
    assertThatCode(
            () ->
                StudentGuardianLink.requireLinkable(student(branchId), guardian(branchId), "test"))
        .doesNotThrowAnyException();
  }

  private Student student(UUID studentBranchId) {
    return Student.register(
        studentId,
        studentBranchId,
        new PersonalInfo("Ali", "Kaya", null, null),
        "u",
        "1042",
        12,
        TargetExam.YKS);
  }

  private Guardian guardian(UUID guardianBranchId) {
    return Guardian.register(
        guardianId,
        guardianBranchId,
        new PersonalInfo("Ayşe", "Kaya", null, "+905551112233"),
        "905551112233");
  }
}
