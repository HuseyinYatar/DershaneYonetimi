package com.dershane.userservice.enrollment.adapter.in.web;

import com.dershane.userservice.enrollment.adapter.in.web.dto.AssignClassroomRequest;
import com.dershane.userservice.enrollment.adapter.in.web.dto.ClassroomMembershipResponse;
import com.dershane.userservice.enrollment.adapter.in.web.dto.ClassroomStudentResponse;
import com.dershane.userservice.enrollment.adapter.in.web.dto.ClassroomTeacherResponse;
import com.dershane.userservice.enrollment.adapter.in.web.dto.CreateTeacherAssignmentRequest;
import com.dershane.userservice.enrollment.adapter.in.web.dto.MyClassroomResponse;
import com.dershane.userservice.enrollment.adapter.in.web.dto.TeacherAssignmentResponse;
import com.dershane.userservice.enrollment.application.port.in.AssignClassroomCommand;
import com.dershane.userservice.enrollment.application.port.in.AssignTeacherCommand;
import com.dershane.userservice.enrollment.application.port.in.ClassroomStudentView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomTeacherView;
import com.dershane.userservice.enrollment.application.port.in.MembershipView;
import com.dershane.userservice.enrollment.application.port.in.TeacherAssignmentView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface EnrollmentWebMapper {

  AssignClassroomCommand toCommand(AssignClassroomRequest request);

  AssignTeacherCommand toCommand(CreateTeacherAssignmentRequest request);

  ClassroomMembershipResponse toResponse(MembershipView view);

  TeacherAssignmentResponse toResponse(TeacherAssignmentView view);

  ClassroomStudentResponse toResponse(ClassroomStudentView view);

  ClassroomTeacherResponse toResponse(ClassroomTeacherView view);

  @Mapping(target = "activeStudentCount", ignore = true)
  MyClassroomResponse toResponse(ClassroomSummaryView view);
}
