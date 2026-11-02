package com.dershane.attendanceservice.excuse.adapter.in.web;

import com.dershane.attendanceservice.excuse.adapter.in.web.dto.ExcuseResponse;
import com.dershane.attendanceservice.excuse.adapter.in.web.dto.RequestExcuseRequest;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseView;
import com.dershane.attendanceservice.excuse.application.port.in.RequestExcuseCommand;
import org.mapstruct.Mapper;

@Mapper
interface ExcuseWebMapper {

  RequestExcuseCommand toCommand(RequestExcuseRequest request);

  ExcuseResponse toResponse(ExcuseView view);
}
