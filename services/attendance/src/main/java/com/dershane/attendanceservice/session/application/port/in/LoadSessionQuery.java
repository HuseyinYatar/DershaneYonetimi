package com.dershane.attendanceservice.session.application.port.in;

import com.dershane.attendanceservice.session.domain.ClassSession;
import java.util.UUID;

/** For other modules: loads a session and rejects (with a log line) when it does not exist. */
public interface LoadSessionQuery {

  ClassSession require(UUID sessionId);
}
