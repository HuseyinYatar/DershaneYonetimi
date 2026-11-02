package com.dershane.attendanceservice.excuse.adapter.out.persistence;

import com.dershane.attendanceservice.excuse.application.port.in.ExcuseView;
import com.dershane.attendanceservice.excuse.application.port.out.ExcuseQueryPort;
import com.dershane.attendanceservice.excuse.application.port.out.LoadExcusePort;
import com.dershane.attendanceservice.excuse.application.port.out.SaveExcusePort;
import com.dershane.attendanceservice.excuse.domain.Excuse;
import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import com.dershane.attendanceservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ExcusePersistenceAdapter implements LoadExcusePort, SaveExcusePort, ExcuseQueryPort {

  private final ExcuseJpaRepository repository;
  private final ExcusePersistenceMapper mapper;

  @Override
  public Optional<Excuse> findById(UUID excuseId) {
    return repository.findById(excuseId).map(mapper::toDomain);
  }

  @Override
  public Map<UUID, UUID> findApprovedCoverage(Collection<UUID> studentIds, LocalDate date) {
    Map<UUID, UUID> byStudent = new HashMap<>();
    // Ordered by decision time: the earliest approved excuse wins.
    repository
        .findCovering(studentIds, ExcuseStatus.APPROVED, date)
        .forEach(e -> byStudent.putIfAbsent(e.getStudentId(), e.getId()));
    return byStudent;
  }

  @Override
  public Excuse save(Excuse excuse) {
    if (excuse.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(excuse)));
    }
    ExcuseJpaEntity entity =
        repository
            .findById(excuse.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.EXCUSE_NOT_FOUND,
                        "Excuse to save no longer exists",
                        "excuseId",
                        excuse.getId()));
    VersionChecks.requireCurrent(
        log, "Excuse", excuse.getId(), excuse.getVersion(), entity.getVersion());
    mapper.update(entity, excuse);
    return mapper.toDomain(entity);
  }

  @Override
  public Optional<ExcuseView> findViewById(UUID excuseId) {
    return repository.findById(excuseId).map(mapper::toView);
  }

  @Override
  public Page<ExcuseView> findViews(
      UUID branchId, UUID requestedBy, UUID studentId, ExcuseStatus status, Pageable pageable) {
    return repository
        .findAll(matching(branchId, requestedBy, studentId, status), pageable)
        .map(mapper::toView);
  }

  /** Null filters are not applied. */
  private static Specification<ExcuseJpaEntity> matching(
      UUID branchId, UUID requestedBy, UUID studentId, ExcuseStatus status) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (branchId != null) {
        predicates.add(cb.equal(root.get("branchId"), branchId));
      }
      if (requestedBy != null) {
        predicates.add(cb.equal(root.get("requestedBy"), requestedBy));
      }
      if (studentId != null) {
        predicates.add(cb.equal(root.get("studentId"), studentId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
