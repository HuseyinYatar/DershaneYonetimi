package com.dershane.userservice.branch.adapter.in.web;

import com.dershane.userservice.branch.adapter.in.web.dto.BranchResponse;
import com.dershane.userservice.branch.adapter.in.web.dto.CreateBranchRequest;
import com.dershane.userservice.branch.adapter.in.web.dto.UpdateBranchRequest;
import com.dershane.userservice.branch.application.port.in.BranchView;
import com.dershane.userservice.branch.application.port.in.CreateBranchCommand;
import com.dershane.userservice.branch.application.port.in.UpdateBranchCommand;
import org.mapstruct.Mapper;

@Mapper
interface BranchWebMapper {

  CreateBranchCommand toCommand(CreateBranchRequest request);

  UpdateBranchCommand toCommand(UpdateBranchRequest request);

  BranchResponse toResponse(BranchView view);
}
