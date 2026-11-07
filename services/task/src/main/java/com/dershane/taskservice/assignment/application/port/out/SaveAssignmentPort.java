package com.dershane.taskservice.assignment.application.port.out;

import com.dershane.taskservice.assignment.domain.Assignment;

public interface SaveAssignmentPort {

  /**
   * Inserts a new assignment or updates a loaded one together with its submission and evaluation;
   * returns it with id and current version.
   */
  Assignment save(Assignment assignment);
}
