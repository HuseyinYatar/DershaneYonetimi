package com.dershane.userservice.enrollment.domain;

import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.subject.domain.Subject;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.Teacher;
import lombok.extern.slf4j.Slf4j;

/** Who may be put into or assigned to which classroom. Every rejection is logged. */
@Slf4j
public final class EnrollmentPolicy {

  private EnrollmentPolicy() {}

  /** The student must be active, and the classroom active, in the student's branch and grade. */
  public static void requireCanEnroll(Student student, Classroom classroom, String action) {
    student.requireActive(action);
    classroom.requireActiveInBranch(student.getBranchId(), action);
    if (classroom.getGradeLevel() != student.getGradeLevel()) {
      throw Rejections.reject(
          log,
          ErrorCode.GRADE_LEVEL_MISMATCH,
          "Enrollment rejected: student grade level differs from classroom grade level",
          "studentId",
          student.getId(),
          "classroomId",
          classroom.getId(),
          "studentGrade",
          student.getGradeLevel(),
          "classroomGrade",
          classroom.getGradeLevel());
    }
  }

  /** Teacher, classroom and subject must be active; the classroom is in the teacher's branch. */
  public static void requireCanAssign(
      Teacher teacher, Classroom classroom, Subject subject, String action) {
    teacher.requireActive(action);
    classroom.requireActiveInBranch(teacher.getBranchId(), action);
    subject.requireActive(action);
  }
}
