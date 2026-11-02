package com.dershane.attendanceservice.excuse.adapter.out.persistence;

import com.dershane.attendanceservice.excuse.application.port.in.ExcuseView;
import com.dershane.attendanceservice.excuse.domain.Excuse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface ExcusePersistenceMapper {

  Excuse toDomain(ExcuseJpaEntity entity);

  ExcuseJpaEntity toEntity(Excuse excuse);

  /** Only the decision changes after an excuse is reported. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "studentId", ignore = true)
  @Mapping(target = "branchId", ignore = true)
  @Mapping(target = "fromDate", ignore = true)
  @Mapping(target = "toDate", ignore = true)
  @Mapping(target = "reason", ignore = true)
  @Mapping(target = "requestedBy", ignore = true)
  void update(@MappingTarget ExcuseJpaEntity entity, Excuse excuse);

  ExcuseView toView(ExcuseJpaEntity entity);
}
