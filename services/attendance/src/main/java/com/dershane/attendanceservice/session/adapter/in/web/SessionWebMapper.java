package com.dershane.attendanceservice.session.adapter.in.web;

import com.dershane.attendanceservice.session.adapter.in.web.dto.CreateSessionRequest;
import com.dershane.attendanceservice.session.adapter.in.web.dto.SessionResponse;
import com.dershane.attendanceservice.session.application.port.in.CreateAdHocSessionCommand;
import com.dershane.attendanceservice.session.application.port.in.SessionView;
import org.mapstruct.Mapper;

@Mapper
interface SessionWebMapper {

  CreateAdHocSessionCommand toCommand(CreateSessionRequest request);

  SessionResponse toResponse(SessionView view);
}
