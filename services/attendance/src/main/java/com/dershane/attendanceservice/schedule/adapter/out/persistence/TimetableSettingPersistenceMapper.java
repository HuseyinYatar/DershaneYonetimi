package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.schedule.domain.TimetableSetting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface TimetableSettingPersistenceMapper {

  TimetableSetting toDomain(TimetableSettingJpaEntity entity);

  TimetableSettingJpaEntity toEntity(TimetableSetting setting);

  /** The branch is the key and never changes; version and audit fields are managed by JPA. */
  @Mapping(target = "branchId", ignore = true)
  void update(@MappingTarget TimetableSettingJpaEntity entity, TimetableSetting setting);
}
