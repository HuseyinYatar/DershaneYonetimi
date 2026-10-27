package com.dershane.userservice.classroom.application.port.in;

import com.dershane.userservice.classroom.domain.Classroom;
import java.util.UUID;

/** For other modules: loads a classroom and rejects (with a log line) when it does not exist. */
public interface LoadClassroomQuery {

  Classroom require(UUID classroomId);
}
