package com.dershane.userservice.subject.application.port.in;

import java.util.UUID;

public interface DeactivateSubjectUseCase {

  /** Deactivating keeps existing teacher assignments (history) but blocks new ones. */
  SubjectView deactivate(UUID subjectId);
}
