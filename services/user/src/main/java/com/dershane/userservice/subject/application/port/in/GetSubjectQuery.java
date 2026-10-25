package com.dershane.userservice.subject.application.port.in;

import java.util.UUID;

public interface GetSubjectQuery {

  SubjectView get(UUID subjectId);
}
