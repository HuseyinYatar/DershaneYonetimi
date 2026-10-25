package com.dershane.userservice.user.domain;

import java.util.UUID;
import lombok.Getter;

/** Diagram: Ogretmen. */
@Getter
public class Teacher extends User {

  private UUID specialtySubjectId;

  /** Rehydrates a saved teacher. Use {@link #register} for a new one. */
  public Teacher(
      UUID id,
      Long version,
      UUID branchId,
      PersonalInfo info,
      String username,
      boolean active,
      UUID specialtySubjectId) {
    super(id, version, branchId, Role.TEACHER, info, username, active);
    this.specialtySubjectId = specialtySubjectId;
  }

  public static Teacher register(
      UUID id, UUID branchId, PersonalInfo info, String username, UUID specialtySubjectId) {
    return new Teacher(id, null, branchId, info, username, true, specialtySubjectId);
  }

  public void changeSpecialty(UUID subjectId) {
    this.specialtySubjectId = subjectId;
  }
}
