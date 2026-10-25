package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.domain.StudentGuardian;

public interface SaveStudentGuardianPort {

  /** Inserts a new link or updates a loaded one. */
  StudentGuardian save(StudentGuardian link);

  /**
   * Deletes and flushes at once: Hibernate runs inserts before deletes, and {@code student_id} is
   * unique, so the old link must be gone before the new one is inserted.
   */
  void deleteAndFlush(StudentGuardian link);
}
