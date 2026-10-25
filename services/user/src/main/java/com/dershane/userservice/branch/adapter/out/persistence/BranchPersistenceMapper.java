package com.dershane.userservice.branch.adapter.out.persistence;

import com.dershane.userservice.branch.application.port.in.BranchView;
import com.dershane.userservice.branch.domain.Branch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface BranchPersistenceMapper {

  Branch toDomain(BranchJpaEntity entity);

  BranchJpaEntity toEntity(Branch branch);

  /** Id and code never change; version and audit fields are managed by JPA. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "code", ignore = true)
  void update(@MappingTarget BranchJpaEntity entity, Branch branch);

  BranchView toView(BranchJpaEntity entity);
}
