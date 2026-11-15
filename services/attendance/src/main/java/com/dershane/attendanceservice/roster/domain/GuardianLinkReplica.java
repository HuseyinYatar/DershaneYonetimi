package com.dershane.attendanceservice.roster.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** The guardian of a student, copied from the user service. A student has at most one. */
@Getter
public class GuardianLinkReplica {

  private final UUID studentId;

  /** Version the link was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID guardianId;

  /**
   * Time of the user service event that set this guardian. Events are not ordered, so an older
   * event must not overwrite a newer one. Null for rows copied before the column existed.
   */
  private final Instant linkedAt;

  public GuardianLinkReplica(UUID studentId, Long version, UUID guardianId, Instant linkedAt) {
    this.studentId = studentId;
    this.version = version;
    this.guardianId = guardianId;
    this.linkedAt = linkedAt;
  }

  public static GuardianLinkReplica of(UUID studentId, UUID guardianId, Instant linkedAt) {
    return new GuardianLinkReplica(studentId, null, guardianId, linkedAt);
  }

  /**
   * The stored link was made after {@code eventTime}, so an event from {@code eventTime} is stale.
   */
  public boolean isNewerThan(Instant eventTime) {
    return linkedAt != null && linkedAt.isAfter(eventTime);
  }
}
