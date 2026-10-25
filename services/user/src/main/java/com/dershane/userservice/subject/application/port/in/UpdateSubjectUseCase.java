package com.dershane.userservice.subject.application.port.in;

import java.util.UUID;

public interface UpdateSubjectUseCase {

  SubjectView update(UUID subjectId, UpdateSubjectCommand command);
}
