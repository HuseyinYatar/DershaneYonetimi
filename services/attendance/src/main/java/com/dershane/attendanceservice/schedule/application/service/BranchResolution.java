package com.dershane.attendanceservice.schedule.application.service;

import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/** The branch rule of the timetable endpoints, in one place. */
@Slf4j
final class BranchResolution {

  private BranchResolution() {}

  /** Same branch rule as the schedule list; a super admin with no branch must name one. */
  static UUID resolve(BranchAccessPolicy access, Actor actor, UUID requested, String action) {
    UUID branch = access.resolveBranchFilter(actor, requested, action);
    if (branch == null) {
      throw Rejections.reject(
          log,
          ErrorCode.VALIDATION_FAILED,
          "Timetable endpoint needs a branch id",
          "callerId",
          actor.id(),
          "action",
          action);
    }
    return branch;
  }
}
