package com.dershane.userservice.subject.application.port.out;

import com.dershane.userservice.subject.domain.Subject;

public interface SaveSubjectPort {

  /** Inserts a new subject or updates a loaded one; returns it with id and current version. */
  Subject save(Subject subject);
}
