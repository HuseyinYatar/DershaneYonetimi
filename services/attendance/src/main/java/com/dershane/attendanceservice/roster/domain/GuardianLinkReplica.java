package com.dershane.attendanceservice.roster.domain;

import java.util.UUID;
import lombok.Getter;

/** The guardian of a student, copied from the user service. A student has at most one. */
@Getter
public class GuardianLinkReplica {

  private final UUID studentId;

  /** Version the link was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID guardianId;

  public GuardianLinkReplica(UUID studentId, Long version, UUID guardianId) {
    this.studentId = studentId;
    this.version = version;
    this.guardianId = guardianId;
  }

  public static GuardianLinkReplica of(UUID studentId, UUID guardianId) {
    return new GuardianLinkReplica(studentId, null, guardianId);
  }
}
