package com.dershane.userservice.subject.adapter.out.persistence;

import com.dershane.userservice.subject.application.port.in.SubjectView;
import com.dershane.userservice.subject.domain.Subject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface SubjectPersistenceMapper {

  Subject toDomain(SubjectJpaEntity entity);

  SubjectJpaEntity toEntity(Subject subject);

  /** Id and code never change; version and audit fields are managed by JPA. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "code", ignore = true)
  void update(@MappingTarget SubjectJpaEntity entity, Subject subject);

  SubjectView toView(SubjectJpaEntity entity);
}
