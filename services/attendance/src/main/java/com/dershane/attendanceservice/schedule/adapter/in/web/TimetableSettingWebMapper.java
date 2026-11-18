package com.dershane.attendanceservice.schedule.adapter.in.web;

import com.dershane.attendanceservice.schedule.adapter.in.web.dto.SaveTimetableSettingRequest;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.TimetableSettingResponse;
import com.dershane.attendanceservice.schedule.application.port.in.SaveTimetableSettingCommand;
import com.dershane.attendanceservice.schedule.application.port.in.TimetableSettingView;
import org.mapstruct.Mapper;

@Mapper
interface TimetableSettingWebMapper {

  SaveTimetableSettingCommand toCommand(SaveTimetableSettingRequest request);

  TimetableSettingResponse toResponse(TimetableSettingView view);
}
