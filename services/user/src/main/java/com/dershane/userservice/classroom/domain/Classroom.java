package com.dershane.userservice.classroom.domain;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A class group in a branch for one term (diagram: Sinif), e.g. "12-A Sayısal", 2026-2027. The
 * grade level is fixed after creation because enrolled students must match it.
 */
@Slf4j
@Getter
public class Classroom {

  /** Null until the classroom is saved for the first time. */
  private final UUID id;

  /** Version the classroom was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID branchId;
  private String name;
  private final int gradeLevel;
  private String term;
  private boolean active;

  /** Rehydrates a saved classroom. Use {@link #create} for a new one. */
  public Classroom(
      UUID id,
      Long version,
      UUID branchId,
      String name,
      int gradeLevel,
      String term,
      boolean active) {
    this.id = id;
    this.version = version;
    this.branchId = branchId;
    this.name = name;
    this.gradeLevel = gradeLevel;
    this.term = term;
    this.active = active;
  }

  public static Classroom create(UUID branchId, String name, int gradeLevel, String term) {
    return new Classroom(null, null, branchId, name, gradeLevel, term, true);
  }

  /** Null arguments leave the current value. */
  public void update(String newName, String newTerm) {
    if (newName != null) {
      this.name = newName;
    }
    if (newTerm != null) {
      this.term = newTerm;
    }
  }

  /**
   * Deactivates the classroom unless students are still enrolled.
   *
   * @return false when the classroom was already inactive (nothing changed)
   */
  public boolean deactivate(boolean hasActiveStudents) {
    if (!active) {
      return false;
    }
    if (hasActiveStudents) {
      throw Rejections.reject(
          log,
          ErrorCode.CLASSROOM_HAS_ACTIVE_STUDENTS,
          "Classroom deactivation rejected: students are still enrolled",
          "classroomId",
          id);
    }
    this.active = false;
    return true;
  }

  public void requireActive(String action) {
    if (!active) {
      throw Rejections.reject(
          log,
          ErrorCode.CLASSROOM_INACTIVE,
          "Operation rejected: classroom is inactive",
          "classroomId",
          id,
          "action",
          action);
    }
  }

  /** Active classroom of the given branch; used before enrolling or assigning. */
  public void requireActiveInBranch(UUID expectedBranchId, String action) {
    requireActive(action);
    if (!branchId.equals(expectedBranchId)) {
      throw Rejections.reject(
          log,
          ErrorCode.BRANCH_MISMATCH,
          "Operation rejected: classroom belongs to another branch",
          "classroomId",
          id,
          "classroomBranchId",
          branchId,
          "expectedBranchId",
          expectedBranchId,
          "action",
          action);
    }
  }
}
