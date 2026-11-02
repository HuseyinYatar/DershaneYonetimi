package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.schedule.application.port.in.ScheduleView;
import com.dershane.attendanceservice.schedule.domain.ClassSchedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface SchedulePersistenceMapper {

  ClassSchedule toDomain(ClassScheduleJpaEntity entity);

  ClassScheduleJpaEntity toEntity(ClassSchedule schedule);

  /** Id, branch and classroom never change; version and audit fields are managed by JPA. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "branchId", ignore = true)
  @Mapping(target = "classroomId", ignore = true)
  void update(@MappingTarget ClassScheduleJpaEntity entity, ClassSchedule schedule);

  ScheduleView toView(ClassScheduleJpaEntity entity);
}
