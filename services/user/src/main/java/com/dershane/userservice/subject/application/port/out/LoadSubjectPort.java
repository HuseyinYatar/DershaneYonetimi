package com.dershane.userservice.subject.application.port.out;

import com.dershane.userservice.subject.domain.Subject;
import java.util.Optional;
import java.util.UUID;

public interface LoadSubjectPort {

  Optional<Subject> findById(UUID subjectId);

  boolean existsByCode(String code);
}
