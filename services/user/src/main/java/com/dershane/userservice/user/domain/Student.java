package com.dershane.userservice.user.domain;

import java.util.UUID;
import lombok.Getter;

/** Diagram: Ogrenci. */
@Getter
public class Student extends User {

  private String studentNumber;
  private int gradeLevel;
  private TargetExam targetExam;

  /** Rehydrates a saved student. Use {@link #register} for a new one. */
  public Student(
      UUID id,
      Long version,
      UUID branchId,
      PersonalInfo info,
      String username,
      boolean active,
      String studentNumber,
      int gradeLevel,
      TargetExam targetExam) {
    super(id, version, branchId, Role.STUDENT, info, username, active);
    this.studentNumber = studentNumber;
    this.gradeLevel = gradeLevel;
    this.targetExam = targetExam;
  }

  public static Student register(
      UUID id,
      UUID branchId,
      PersonalInfo info,
      String username,
      String studentNumber,
      int gradeLevel,
      TargetExam targetExam) {
    return new Student(
        id, null, branchId, info, username, true, studentNumber, gradeLevel, targetExam);
  }

  /** Null arguments leave the current value. */
  public void updateStudentInfo(
      String newStudentNumber, Integer newGradeLevel, TargetExam newTargetExam) {
    if (newStudentNumber != null) {
      this.studentNumber = newStudentNumber;
    }
    if (newGradeLevel != null) {
      this.gradeLevel = newGradeLevel;
    }
    if (newTargetExam != null) {
      this.targetExam = newTargetExam;
    }
  }
}
