package com.dershane.userservice.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.subject.domain.Subject;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.TargetExam;
import com.dershane.userservice.user.domain.Teacher;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentPolicyTest {

  private final UUID branchId = UUID.randomUUID();
  private final PersonalInfo info = new PersonalInfo("Ali", "Kaya", null, null);

  @Test
  void activeStudentCanJoinAnActiveClassroomOfTheirBranchAndGrade() {
    assertThatCode(
            () -> EnrollmentPolicy.requireCanEnroll(student(12), classroom(branchId, 12), "test"))
        .doesNotThrowAnyException();
  }

  @Test
  void gradeLevelsMustMatch() {
    assertRejected(
        () -> EnrollmentPolicy.requireCanEnroll(student(12), classroom(branchId, 11), "test"),
        ErrorCode.GRADE_LEVEL_MISMATCH);
  }

  @Test
  void classroomMustBeInTheStudentsBranch() {
    assertRejected(
        () ->
            EnrollmentPolicy.requireCanEnroll(
                student(12), classroom(UUID.randomUUID(), 12), "test"),
        ErrorCode.BRANCH_MISMATCH);
  }

  @Test
  void inactiveStudentCannotJoin() {
    Student student = student(12);
    student.deactivate();

    assertRejected(
        () -> EnrollmentPolicy.requireCanEnroll(student, classroom(branchId, 12), "test"),
        ErrorCode.USER_INACTIVE);
  }

  @Test
  void inactiveClassroomCannotTakeStudents() {
    Classroom classroom = classroom(branchId, 12);
    classroom.deactivate(false);

    assertRejected(
        () -> EnrollmentPolicy.requireCanEnroll(student(12), classroom, "test"),
        ErrorCode.CLASSROOM_INACTIVE);
  }

  @Test
  void teacherCanBeAssignedToAnActiveSubjectInAnActiveClassroomOfTheirBranch() {
    assertThatCode(
            () ->
                EnrollmentPolicy.requireCanAssign(
                    teacher(), classroom(branchId, 12), Subject.create("Matematik", "MAT"), "test"))
        .doesNotThrowAnyException();
  }

  @Test
  void inactiveSubjectCannotBeAssigned() {
    Subject subject = Subject.create("Matematik", "MAT");
    subject.deactivate();

    assertRejected(
        () -> EnrollmentPolicy.requireCanAssign(teacher(), classroom(branchId, 12), subject, "t"),
        ErrorCode.SUBJECT_INACTIVE);
  }

  @Test
  void inactiveTeacherCannotBeAssigned() {
    Teacher teacher = teacher();
    teacher.deactivate();

    assertRejected(
        () ->
            EnrollmentPolicy.requireCanAssign(
                teacher, classroom(branchId, 12), Subject.create("Matematik", "MAT"), "test"),
        ErrorCode.USER_INACTIVE);
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(expected);
  }

  private Student student(int grade) {
    return Student.register(UUID.randomUUID(), branchId, info, "u", "1042", grade, TargetExam.YKS);
  }

  private Teacher teacher() {
    return Teacher.register(UUID.randomUUID(), branchId, info, "t@example.com", UUID.randomUUID());
  }

  private Classroom classroom(UUID classroomBranchId, int grade) {
    return Classroom.create(classroomBranchId, "12-A", grade, "2026-2027");
  }
}
