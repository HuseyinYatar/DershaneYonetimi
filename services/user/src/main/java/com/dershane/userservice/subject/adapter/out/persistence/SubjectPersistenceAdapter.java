package com.dershane.userservice.subject.adapter.out.persistence;

import com.dershane.userservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.subject.application.port.in.SubjectView;
import com.dershane.userservice.subject.application.port.out.LoadSubjectPort;
import com.dershane.userservice.subject.application.port.out.SaveSubjectPort;
import com.dershane.userservice.subject.application.port.out.SubjectQueryPort;
import com.dershane.userservice.subject.domain.Subject;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class SubjectPersistenceAdapter
    implements LoadSubjectPort, SaveSubjectPort, SubjectQueryPort {

  private final SubjectJpaRepository repository;
  private final SubjectPersistenceMapper mapper;

  @Override
  public Optional<Subject> findById(UUID subjectId) {
    return repository.findById(subjectId).map(mapper::toDomain);
  }

  @Override
  public boolean existsByCode(String code) {
    return repository.existsByCode(code);
  }

  @Override
  public Subject save(Subject subject) {
    if (subject.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(subject)));
    }
    SubjectJpaEntity entity =
        repository
            .findById(subject.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.SUBJECT_NOT_FOUND,
                        "Subject to save no longer exists",
                        "subjectId",
                        subject.getId()));
    VersionChecks.requireCurrent(
        log, "Subject", subject.getId(), subject.getVersion(), entity.getVersion());
    mapper.update(entity, subject);
    return mapper.toDomain(entity);
  }

  @Override
  public Optional<SubjectView> findViewById(UUID subjectId) {
    return repository.findById(subjectId).map(mapper::toView);
  }

  @Override
  public Page<SubjectView> findViews(Boolean active, Pageable pageable) {
    Page<SubjectJpaEntity> page =
        active == null ? repository.findAll(pageable) : repository.findByActive(active, pageable);
    return page.map(mapper::toView);
  }
}
