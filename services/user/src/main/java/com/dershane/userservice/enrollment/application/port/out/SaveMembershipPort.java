package com.dershane.userservice.enrollment.application.port.out;

import com.dershane.userservice.enrollment.domain.ClassroomMembership;

public interface SaveMembershipPort {

  /** Inserts a new membership or updates a loaded one. */
  ClassroomMembership save(ClassroomMembership membership);

  /**
   * Saves and flushes at once. Hibernate runs inserts before updates and the partial unique index
   * allows one open membership per student, so the old membership must be closed in the database
   * before the new one is inserted.
   */
  ClassroomMembership saveAndFlush(ClassroomMembership membership);
}
