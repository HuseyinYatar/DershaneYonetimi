package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.schedule.application.port.in.ScheduleView;
import com.dershane.attendanceservice.schedule.application.port.out.LoadSchedulePort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveSchedulePort;
import com.dershane.attendanceservice.schedule.application.port.out.ScheduleQueryPort;
import com.dershane.attendanceservice.schedule.domain.ClassSchedule;
import com.dershane.attendanceservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.LocalDate;
import java.util.List;
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
public class SchedulePersistenceAdapter
    implements LoadSchedulePort, SaveSchedulePort, ScheduleQueryPort {

  private final ClassScheduleJpaRepository repository;
  private final SchedulePersistenceMapper mapper;

  @Override
  public Optional<ClassSchedule> findById(UUID scheduleId) {
    return repository.findById(scheduleId).map(mapper::toDomain);
  }

  @Override
  public List<ClassSchedule> findActiveValidBetween(LocalDate from, LocalDate to) {
    return repository.findActiveValidBetween(from, to).stream().map(mapper::toDomain).toList();
  }

  @Override
  public ClassSchedule save(ClassSchedule schedule) {
    if (schedule.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(schedule)));
    }
    ClassScheduleJpaEntity entity =
        repository
            .findById(schedule.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.SCHEDULE_NOT_FOUND,
                        "Schedule to save no longer exists",
                        "scheduleId",
                        schedule.getId()));
    VersionChecks.requireCurrent(
        log, "ClassSchedule", schedule.getId(), schedule.getVersion(), entity.getVersion());
    mapper.update(entity, schedule);
    return mapper.toDomain(entity);
  }

  @Override
  public Optional<ScheduleView> findViewById(UUID scheduleId) {
    return repository.findById(scheduleId).map(mapper::toView);
  }

  @Override
  public Page<ScheduleView> findViews(
      UUID branchId, UUID classroomId, UUID teacherId, Boolean active, Pageable pageable) {
    return repository
        .findAll(matching(branchId, classroomId, teacherId, active), pageable)
        .map(mapper::toView);
  }

  /** Null filters are not applied. */
  private static Specification<ClassScheduleJpaEntity> matching(
      UUID branchId, UUID classroomId, UUID teacherId, Boolean active) {
    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
      if (branchId != null) {
        predicates.add(cb.equal(root.get("branchId"), branchId));
      }
      if (classroomId != null) {
        predicates.add(cb.equal(root.get("classroomId"), classroomId));
      }
      if (teacherId != null) {
        predicates.add(cb.equal(root.get("teacherId"), teacherId));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }
}
