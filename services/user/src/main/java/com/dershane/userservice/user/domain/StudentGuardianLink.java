package com.dershane.userservice.user.domain;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Rules for linking a guardian to a student. A student has at most one guardian: linking a
 * different guardian replaces the old link.
 */
@Slf4j
public final class StudentGuardianLink {

  /** What linking a guardian changes. */
  public sealed interface Change {}

  /** The student had no guardian. */
  public record Created(StudentGuardian link) implements Change {}

  /** A different guardian was linked before; {@code previous} must be removed first. */
  public record Replaced(StudentGuardian previous, StudentGuardian link) implements Change {}

  /** The same guardian stays, but the relationship changed. */
  public record RelationshipChanged(StudentGuardian link) implements Change {}

  /** The same guardian with the same relationship was already linked. */
  public record Unchanged(StudentGuardian link) implements Change {}

  private StudentGuardianLink() {}

  /** Both must be active and in the same branch. */
  public static void requireLinkable(Student student, Guardian guardian, String action) {
    student.requireActive(action);
    guardian.requireActive(action);
    if (!guardian.getBranchId().equals(student.getBranchId())) {
      throw Rejections.reject(
          log,
          ErrorCode.BRANCH_MISMATCH,
          "Guardian link rejected: guardian and student are in different branches",
          "studentId",
          student.getId(),
          "guardianId",
          guardian.getId());
    }
  }

  /** Decides how the student's link changes when {@code guardianId} is linked. */
  public static Change replaceWith(
      Optional<StudentGuardian> current,
      UUID studentId,
      UUID guardianId,
      Relationship relationship) {
    if (current.isPresent() && current.get().getGuardianId().equals(guardianId)) {
      StudentGuardian existing = current.get();
      return existing.changeRelationship(relationship)
          ? new RelationshipChanged(existing)
          : new Unchanged(existing);
    }
    StudentGuardian link = StudentGuardian.link(guardianId, studentId, relationship);
    return current.<Change>map(previous -> new Replaced(previous, link)).orElse(new Created(link));
  }
}
