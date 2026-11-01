package com.dershane.attendanceservice.schedule.adapter.in.web;

import com.dershane.attendanceservice.schedule.adapter.in.web.dto.CreateScheduleRequest;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.ScheduleResponse;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.UpdateScheduleRequest;
import com.dershane.attendanceservice.schedule.application.port.in.CreateScheduleCommand;
import com.dershane.attendanceservice.schedule.application.port.in.ScheduleView;
import com.dershane.attendanceservice.schedule.application.port.in.UpdateScheduleCommand;
import org.mapstruct.Mapper;

@Mapper
interface ScheduleWebMapper {

  CreateScheduleCommand toCommand(CreateScheduleRequest request);

  UpdateScheduleCommand toCommand(UpdateScheduleRequest request);

  ScheduleResponse toResponse(ScheduleView view);
}
