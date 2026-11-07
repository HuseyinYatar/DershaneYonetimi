package com.dershane.taskservice.task.adapter.out.persistence;

import com.dershane.taskservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.shared.domain.Rejections;
import com.dershane.taskservice.task.application.port.in.TaskView;
import com.dershane.taskservice.task.application.port.out.LoadTaskPort;
import com.dershane.taskservice.task.application.port.out.SaveTaskPort;
import com.dershane.taskservice.task.application.port.out.TaskQueryPort;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
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
public class TaskPersistenceAdapter implements LoadTaskPort, SaveTaskPort, TaskQueryPort {

  private final TaskJpaRepository repository;
  private final TaskPersistenceMapper mapper;

  @Override
  public Optional<Task> findById(UUID taskId) {
    return repository.findById(taskId).map(mapper::toDomain);
  }

  @Override
  public List<Task> findActiveOn(LocalDate date) {
    return repository.findActiveOn(date).stream().map(mapper::toDomain).toList();
  }

  @Override
  public List<Task> findActiveTargeting(TargetType targetType, UUID targetId, LocalDate date) {
    return repository.findActiveTargeting(targetType, targetId, date).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public Task save(Task task) {
    if (task.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(task)));
    }
    TaskJpaEntity entity =
        repository
            .findById(task.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.TASK_NOT_FOUND,
                        "Task to save no longer exists",
                        "taskId",
                        task.getId()));
    VersionChecks.requireCurrent(log, "Task", task.getId(), task.getVersion(), entity.getVersion());
    mapper.update(entity, task);
    return mapper.toDomain(entity);
  }

  @Override
  public Optional<TaskView> findViewById(UUID taskId) {
    return repository.findById(taskId).map(mapper::toView);
  }

  @Override
  public Page<TaskView> findViews(
      UUID branchId, UUID teacherId, Boolean active, Pageable pageable) {
    return repository.findAll(matching(branchId, teacherId, active), pageable).map(mapper::toView);
  }

  /** Null filters are not applied. */
  private static Specification<TaskJpaEntity> matching(
      UUID branchId, UUID teacherId, Boolean active) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (branchId != null) {
        predicates.add(cb.equal(root.get("branchId"), branchId));
      }
      if (teacherId != null) {
        predicates.add(cb.equal(root.get("teacherId"), teacherId));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
