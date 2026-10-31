package com.dershane.attendanceservice.roster.adapter.in.web;

import com.dershane.attendanceservice.roster.adapter.in.web.dto.MembershipReplicaRequest;
import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipAddedCommand;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface RosterWebMapper {

  @Mapping(target = "membershipId", source = "membershipId")
  RecordMembershipAddedCommand toCommand(UUID membershipId, MembershipReplicaRequest request);
}
