package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface RemoveDraftLessonUseCase {

  void remove(Actor actor, UUID branchId, UUID lessonId);
}
