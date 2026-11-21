package com.dershane.attendanceservice.schedule.adapter.in.web;

import com.dershane.attendanceservice.schedule.adapter.in.web.dto.DraftLessonResponse;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.DraftMissingResponse;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.GenerateTimetableDraftRequest;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.TimetableDraftResponse;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateTimetableDraftCommand;
import com.dershane.attendanceservice.schedule.application.port.in.TimetableDraftView;
import org.mapstruct.Mapper;

@Mapper
interface TimetableDraftWebMapper {

  GenerateTimetableDraftCommand toCommand(GenerateTimetableDraftRequest request);

  TimetableDraftResponse toResponse(TimetableDraftView view);

  DraftLessonResponse toResponse(TimetableDraftView.Lesson lesson);

  DraftMissingResponse toResponse(TimetableDraftView.Missing missing);
}
