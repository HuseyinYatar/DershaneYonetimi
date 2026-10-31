package com.dershane.attendanceservice.session.application.port.in;

import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.shared.application.Actor;

/**
 * Who may work with a session. The rule lives here once; the attendance module uses it for the
 * roster, taking attendance and corrections.
 */
public interface SessionAccessGuard {

  /** The session's own teacher and admins of its branch (super admins: any branch). */
  void requireStaffOf(Actor actor, ClassSession session, String action);

  /** Whether the caller is a super admin or an admin of the session's branch. Never throws. */
  boolean actsAsAdmin(Actor actor, ClassSession session);
}
