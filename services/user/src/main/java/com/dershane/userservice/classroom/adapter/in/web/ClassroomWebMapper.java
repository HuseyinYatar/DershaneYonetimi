package com.dershane.userservice.classroom.adapter.in.web;

import com.dershane.userservice.classroom.adapter.in.web.dto.ClassroomResponse;
import com.dershane.userservice.classroom.adapter.in.web.dto.CreateClassroomRequest;
import com.dershane.userservice.classroom.adapter.in.web.dto.UpdateClassroomRequest;
import com.dershane.userservice.classroom.application.port.in.ClassroomView;
import com.dershane.userservice.classroom.application.port.in.CreateClassroomCommand;
import com.dershane.userservice.classroom.application.port.in.UpdateClassroomCommand;
import org.mapstruct.Mapper;

@Mapper
interface ClassroomWebMapper {

  CreateClassroomCommand toCommand(CreateClassroomRequest request);

  UpdateClassroomCommand toCommand(UpdateClassroomRequest request);

  ClassroomResponse toResponse(ClassroomView view);
}
