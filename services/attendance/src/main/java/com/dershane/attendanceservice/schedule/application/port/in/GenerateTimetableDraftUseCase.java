package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;

public interface GenerateTimetableDraftUseCase {

  /**
   * Distributes the hours the branch's classrooms still need around the existing timetable and
   * keeps the result as the branch's draft, replacing any earlier one.
   */
  TimetableDraftView generate(Actor actor, GenerateTimetableDraftCommand command);
}
