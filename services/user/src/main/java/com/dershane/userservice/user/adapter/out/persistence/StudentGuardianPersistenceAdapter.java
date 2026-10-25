package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.in.GuardianChildView;
import com.dershane.userservice.user.application.port.in.StudentGuardianView;
import com.dershane.userservice.user.application.port.out.LoadStudentGuardianPort;
import com.dershane.userservice.user.application.port.out.SaveStudentGuardianPort;
import com.dershane.userservice.user.application.port.out.StudentGuardianQueryPort;
import com.dershane.userservice.user.domain.StudentGuardian;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StudentGuardianPersistenceAdapter
    implements LoadStudentGuardianPort, SaveStudentGuardianPort, StudentGuardianQueryPort {

  private final StudentGuardianJpaRepository repository;
  private final StudentGuardianPersistenceMapper mapper;

  @Override
  public Optional<StudentGuardian> findByStudentId(UUID studentId) {
    return repository.findByStudentId(studentId).map(mapper::toDomain);
  }

  @Override
  public boolean existsByGuardianIdAndStudentId(UUID guardianId, UUID studentId) {
    return repository.existsByGuardianIdAndStudentId(guardianId, studentId);
  }

  @Override
  public StudentGuardian save(StudentGuardian link) {
    if (link.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(link)));
    }
    StudentGuardianJpaEntity entity = requireEntity(link, "Student guardian link to save");
    VersionChecks.requireCurrent(
        log, "StudentGuardian", link.getStudentId(), link.getVersion(), entity.getVersion());
    mapper.update(entity, link);
    return mapper.toDomain(entity);
  }

  @Override
  public void deleteAndFlush(StudentGuardian link) {
    repository.delete(requireEntity(link, "Student guardian link to delete"));
    repository.flush();
  }

  @Override
  public Optional<StudentGuardianView> findGuardianOfStudent(UUID studentId) {
    return repository.findGuardianView(studentId);
  }

  @Override
  public List<GuardianChildView> findChildren(UUID guardianId) {
    return repository.findChildViews(guardianId);
  }

  private StudentGuardianJpaEntity requireEntity(StudentGuardian link, String what) {
    return repository
        .findById(new StudentGuardianJpaEntity.Key(link.getGuardianId(), link.getStudentId()))
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.STUDENT_GUARDIAN_NOT_FOUND,
                    what + " no longer exists",
                    "studentId",
                    link.getStudentId(),
                    "guardianId",
                    link.getGuardianId()));
  }
}
