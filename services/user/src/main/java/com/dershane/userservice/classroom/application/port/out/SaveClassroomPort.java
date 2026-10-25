package com.dershane.userservice.classroom.application.port.out;

import com.dershane.userservice.classroom.domain.Classroom;

public interface SaveClassroomPort {

  /** Inserts a new classroom or updates a loaded one; returns it with id and current version. */
  Classroom save(Classroom classroom);
}
