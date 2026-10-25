package com.dershane.userservice.user.domain;

import java.util.UUID;
import lombok.Getter;

/** Diagram: VeliOgrenci. A student has at most one guardian. */
@Getter
public class StudentGuardian {

  /** Version the link was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID guardianId;
  private final UUID studentId;
  private Relationship relationship;

  /** Rehydrates a saved link. Use {@link #link} for a new one. */
  public StudentGuardian(Long version, UUID guardianId, UUID studentId, Relationship relationship) {
    this.version = version;
    this.guardianId = guardianId;
    this.studentId = studentId;
    this.relationship = relationship;
  }

  public static StudentGuardian link(UUID guardianId, UUID studentId, Relationship relationship) {
    return new StudentGuardian(null, guardianId, studentId, relationship);
  }

  /** Returns false when the relationship is already the given one. */
  public boolean changeRelationship(Relationship newRelationship) {
    if (relationship == newRelationship) {
      return false;
    }
    this.relationship = newRelationship;
    return true;
  }

  @Override
  public String toString() {
    return "StudentGuardian[guardianId=" + guardianId + ", studentId=" + studentId + "]";
  }
}
