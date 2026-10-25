package com.dershane.userservice.subject.application.port.in;

import com.dershane.userservice.subject.domain.Subject;
import java.util.UUID;

/** For other modules: loads a subject and rejects (with a log line) when it can't be used. */
public interface LoadSubjectQuery {

  Subject require(UUID subjectId);

  Subject requireActive(UUID subjectId, String action);
}
