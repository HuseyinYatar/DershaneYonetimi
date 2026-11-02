package com.dershane.attendanceservice.attendance.adapter.in.web;

import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceEntryRequest;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceRecordResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceSubmissionResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceSummaryResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.CorrectRecordRequest;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.SessionRosterResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.TakeAttendanceRequest;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSubmissionView;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSummaryView;
import com.dershane.attendanceservice.attendance.application.port.in.CorrectRecordCommand;
import com.dershane.attendanceservice.attendance.application.port.in.SessionRosterView;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceCommand;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface AttendanceWebMapper {

  @Mapping(target = "sessionId", source = "sessionId")
  TakeAttendanceCommand toCommand(UUID sessionId, TakeAttendanceRequest request);

  TakeAttendanceCommand.Entry toEntry(AttendanceEntryRequest request);

  @Mapping(target = "sessionId", source = "sessionId")
  @Mapping(target = "studentId", source = "studentId")
  CorrectRecordCommand toCommand(UUID sessionId, UUID studentId, CorrectRecordRequest request);

  AttendanceSubmissionResponse toResponse(AttendanceSubmissionView view);

  AttendanceRecordResponse toResponse(AttendanceRecordView view);

  SessionRosterResponse toResponse(SessionRosterView view);

  AttendanceSummaryResponse toResponse(AttendanceSummaryView view);
}
