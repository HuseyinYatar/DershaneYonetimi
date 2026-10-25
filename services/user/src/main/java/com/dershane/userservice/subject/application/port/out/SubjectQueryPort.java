package com.dershane.userservice.subject.application.port.out;

import com.dershane.userservice.subject.application.port.in.SubjectView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubjectQueryPort {

  Optional<SubjectView> findViewById(UUID subjectId);

  Page<SubjectView> findViews(Boolean active, Pageable pageable);
}
