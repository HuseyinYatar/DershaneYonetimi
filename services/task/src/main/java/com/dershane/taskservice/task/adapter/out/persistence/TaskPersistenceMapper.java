package com.dershane.taskservice.task.adapter.out.persistence;

import com.dershane.taskservice.task.application.port.in.TaskView;
import com.dershane.taskservice.task.domain.Task;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface TaskPersistenceMapper {

  Task toDomain(TaskJpaEntity entity);

  TaskJpaEntity toEntity(Task task);

  /** Only the texts, the attachments and the active flag change after a task is created. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "branchId", ignore = true)
  @Mapping(target = "teacherId", ignore = true)
  @Mapping(target = "targetType", ignore = true)
  @Mapping(target = "targetId", ignore = true)
  @Mapping(target = "recurrence", ignore = true)
  @Mapping(target = "recurrenceDays", ignore = true)
  @Mapping(target = "startDate", ignore = true)
  @Mapping(target = "endDate", ignore = true)
  @Mapping(target = "dueTime", ignore = true)
  void update(@MappingTarget TaskJpaEntity entity, Task task);

  TaskView toView(TaskJpaEntity entity);

  default String[] daysToNames(Set<DayOfWeek> days) {
    return days.stream().map(DayOfWeek::name).toArray(String[]::new);
  }

  default Set<DayOfWeek> namesToDays(String[] names) {
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    Arrays.stream(names).map(DayOfWeek::valueOf).forEach(days::add);
    return days;
  }

  default UUID[] idsToArray(List<UUID> ids) {
    return ids.toArray(UUID[]::new);
  }

  default List<UUID> arrayToIds(UUID[] ids) {
    return List.of(ids);
  }
}
