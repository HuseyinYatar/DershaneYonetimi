package com.dershane.userservice.classroom.adapter.out.persistence;

import com.dershane.userservice.classroom.application.port.in.ClassroomView;
import com.dershane.userservice.classroom.application.port.out.ClassroomQueryPort;
import com.dershane.userservice.classroom.application.port.out.LoadClassroomPort;
import com.dershane.userservice.classroom.application.port.out.SaveClassroomPort;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
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
public class ClassroomPersistenceAdapter
    implements LoadClassroomPort, SaveClassroomPort, ClassroomQueryPort {

  private final ClassroomJpaRepository repository;
  private final ClassroomPersistenceMapper mapper;

  @Override
  public Optional<Classroom> findById(UUID classroomId) {
    return repository.findById(classroomId).map(mapper::toDomain);
  }

  @Override
  public boolean existsByBranchIdAndTermAndName(UUID branchId, String term, String name) {
    return repository.existsByBranchIdAndTermAndName(branchId, term, name);
  }

  @Override
  public Classroom save(Classroom classroom) {
    if (classroom.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(classroom)));
    }
    ClassroomJpaEntity entity =
        repository
            .findById(classroom.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.CLASSROOM_NOT_FOUND,
                        "Classroom to save no longer exists",
                        "classroomId",
                        classroom.getId()));
    VersionChecks.requireCurrent(
        log, "Classroom", classroom.getId(), classroom.getVersion(), entity.getVersion());
    mapper.update(entity, classroom);
    return mapper.toDomain(entity);
  }

  @Override
  public Optional<ClassroomView> findViewById(UUID classroomId) {
    return repository.findById(classroomId).map(mapper::toView);
  }

  @Override
  public Page<ClassroomView> findViews(
      UUID branchId, String term, Boolean active, Pageable pageable) {
    return repository
        .findAll(ClassroomSpecifications.matching(branchId, term, active), pageable)
        .map(mapper::toView);
  }
}
