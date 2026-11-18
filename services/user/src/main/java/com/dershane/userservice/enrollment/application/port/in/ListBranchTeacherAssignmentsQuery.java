package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.List;
import java.util.UUID;

public interface ListBranchTeacherAssignmentsQuery {

  /** Assignments of the branch's active classrooms (admins of that branch, super-admin). */
  List<BranchAssignmentView> listOfBranch(Actor actor, UUID branchId);
}
