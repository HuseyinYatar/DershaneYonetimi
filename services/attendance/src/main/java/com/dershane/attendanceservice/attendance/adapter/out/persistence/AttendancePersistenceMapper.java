package com.dershane.attendanceservice.attendance.adapter.out.persistence;

import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecordChange;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface AttendancePersistenceMapper {

  AttendanceRecord toDomain(AttendanceRecordJpaEntity entity);

  AttendanceRecordJpaEntity toEntity(AttendanceRecord record);

  /** Who, which session and the note never change; version and audit fields belong to JPA. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "sessionId", ignore = true)
  @Mapping(target = "studentId", ignore = true)
  @Mapping(target = "note", ignore = true)
  void update(@MappingTarget AttendanceRecordJpaEntity entity, AttendanceRecord record);

  AttendanceRecordChangeJpaEntity toEntity(AttendanceRecordChange change);
}
