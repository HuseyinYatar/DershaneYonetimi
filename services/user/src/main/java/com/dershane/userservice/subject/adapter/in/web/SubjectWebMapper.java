package com.dershane.userservice.subject.adapter.in.web;

import com.dershane.userservice.subject.adapter.in.web.dto.CreateSubjectRequest;
import com.dershane.userservice.subject.adapter.in.web.dto.SubjectResponse;
import com.dershane.userservice.subject.adapter.in.web.dto.UpdateSubjectRequest;
import com.dershane.userservice.subject.application.port.in.CreateSubjectCommand;
import com.dershane.userservice.subject.application.port.in.SubjectView;
import com.dershane.userservice.subject.application.port.in.UpdateSubjectCommand;
import org.mapstruct.Mapper;

@Mapper
interface SubjectWebMapper {

  CreateSubjectCommand toCommand(CreateSubjectRequest request);

  UpdateSubjectCommand toCommand(UpdateSubjectRequest request);

  SubjectResponse toResponse(SubjectView view);
}
