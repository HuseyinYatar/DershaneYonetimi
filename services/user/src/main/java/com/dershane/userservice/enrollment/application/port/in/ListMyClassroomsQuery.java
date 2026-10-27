package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.List;

public interface ListMyClassroomsQuery {

  /** Teachers: classrooms they teach. Students: their current classroom (zero or one). */
  List<ClassroomSummaryView> myClassrooms(Actor actor);
}
