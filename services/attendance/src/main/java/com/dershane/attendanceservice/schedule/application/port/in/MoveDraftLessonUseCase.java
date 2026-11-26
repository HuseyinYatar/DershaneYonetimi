package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;

public interface MoveDraftLessonUseCase {

  /** Gives a draft lesson a new day and hours; they must fit the grid and clash with nothing. */
  TimetableDraftView.Lesson move(Actor actor, MoveDraftLessonCommand command);
}
