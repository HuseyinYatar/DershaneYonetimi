package com.dershane.attendanceservice.roster.adapter.out.persistence;

import com.dershane.attendanceservice.roster.domain.GuardianLinkReplica;
import com.dershane.attendanceservice.roster.domain.MembershipReplica;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface RosterPersistenceMapper {

  MembershipReplica toDomain(MembershipReplicaJpaEntity entity);

  MembershipReplicaJpaEntity toEntity(MembershipReplica membership);

  /** Only the end date changes after a membership is recorded. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "classroomId", ignore = true)
  @Mapping(target = "studentId", ignore = true)
  @Mapping(target = "branchId", ignore = true)
  @Mapping(target = "startDate", ignore = true)
  @Mapping(target = "studentNumber", ignore = true)
  @Mapping(target = "fullName", ignore = true)
  void update(@MappingTarget MembershipReplicaJpaEntity entity, MembershipReplica membership);

  GuardianLinkReplica toDomain(GuardianLinkReplicaJpaEntity entity);

  GuardianLinkReplicaJpaEntity toEntity(GuardianLinkReplica link);

  @Mapping(target = "studentId", ignore = true)
  void update(@MappingTarget GuardianLinkReplicaJpaEntity entity, GuardianLinkReplica link);
}
