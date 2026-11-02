package com.dershane.attendanceservice.session.adapter.out.persistence;

import com.dershane.attendanceservice.session.application.port.in.SessionView;
import com.dershane.attendanceservice.session.domain.ClassSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface SessionPersistenceMapper {

  ClassSession toDomain(ClassSessionJpaEntity entity);

  ClassSessionJpaEntity toEntity(ClassSession session);

  /** Only the teacher and the attendance outcome change; version and audit fields belong to JPA. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "scheduleId", ignore = true)
  @Mapping(target = "branchId", ignore = true)
  @Mapping(target = "classroomId", ignore = true)
  @Mapping(target = "subjectId", ignore = true)
  @Mapping(target = "sessionDate", ignore = true)
  @Mapping(target = "startsAt", ignore = true)
  @Mapping(target = "endsAt", ignore = true)
  void update(@MappingTarget ClassSessionJpaEntity entity, ClassSession session);

  SessionView toView(ClassSessionJpaEntity entity);
}
