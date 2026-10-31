package com.dershane.attendanceservice.session.application.port.out;

import com.dershane.attendanceservice.session.domain.ClassSession;

public interface SaveSessionPort {

  /** Inserts a new session or updates a loaded one; returns it with id and current version. */
  ClassSession save(ClassSession session);
}
