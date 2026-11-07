package com.dershane.taskservice.assignment.adapter.in.web;

import com.dershane.taskservice.assignment.adapter.in.web.dto.AssignmentDetailResponse;
import com.dershane.taskservice.assignment.adapter.in.web.dto.AssignmentSummaryResponse;
import com.dershane.taskservice.assignment.adapter.in.web.dto.EvaluateAssignmentRequest;
import com.dershane.taskservice.assignment.adapter.in.web.dto.SubmitAssignmentRequest;
import com.dershane.taskservice.assignment.application.port.in.AssignmentDetailView;
import com.dershane.taskservice.assignment.application.port.in.AssignmentSummaryView;
import com.dershane.taskservice.assignment.application.port.in.EvaluateAssignmentCommand;
import com.dershane.taskservice.assignment.application.port.in.SubmitAssignmentCommand;
import org.mapstruct.Mapper;

@Mapper
interface AssignmentWebMapper {

  SubmitAssignmentCommand toCommand(SubmitAssignmentRequest request);

  EvaluateAssignmentCommand toCommand(EvaluateAssignmentRequest request);

  AssignmentSummaryResponse toResponse(AssignmentSummaryView view);

  AssignmentDetailResponse toResponse(AssignmentDetailView view);
}
