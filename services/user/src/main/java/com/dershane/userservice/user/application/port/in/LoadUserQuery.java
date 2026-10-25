package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.Teacher;
import java.util.UUID;

/**
 * For other modules: loads users by role and rejects (with a log line) when the user is missing or
 * has another role.
 */
public interface LoadUserQuery {

  Student requireStudent(UUID userId);

  Teacher requireTeacher(UUID userId);

  /**
   * Loads the student and locks the row until the caller's transaction ends, so classroom and
   * guardian changes of one student run one after another. Needs a running transaction.
   */
  Student lockStudent(UUID userId);
}
