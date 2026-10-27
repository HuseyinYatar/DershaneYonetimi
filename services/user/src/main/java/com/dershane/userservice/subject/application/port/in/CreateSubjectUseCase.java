package com.dershane.userservice.subject.application.port.in;

public interface CreateSubjectUseCase {

  SubjectView create(CreateSubjectCommand command);
}
