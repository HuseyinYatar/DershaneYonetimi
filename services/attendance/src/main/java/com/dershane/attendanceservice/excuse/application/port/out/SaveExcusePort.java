package com.dershane.attendanceservice.excuse.application.port.out;

import com.dershane.attendanceservice.excuse.domain.Excuse;

public interface SaveExcusePort {

  /** Inserts a new excuse or updates a loaded one; returns it with id and current version. */
  Excuse save(Excuse excuse);
}
