package com.dershane.userservice.subject.application.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListSubjectsQuery {

  /** {@code active == null} lists all subjects. */
  Page<SubjectView> list(Boolean active, Pageable pageable);
}
