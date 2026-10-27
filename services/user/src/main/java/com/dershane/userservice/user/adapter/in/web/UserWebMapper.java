package com.dershane.userservice.user.adapter.in.web;

import com.dershane.userservice.user.adapter.in.web.dto.CreateAdminRequest;
import com.dershane.userservice.user.adapter.in.web.dto.CreateGuardianRequest;
import com.dershane.userservice.user.adapter.in.web.dto.CreateStudentRequest;
import com.dershane.userservice.user.adapter.in.web.dto.CreateTeacherRequest;
import com.dershane.userservice.user.adapter.in.web.dto.GuardianChildResponse;
import com.dershane.userservice.user.adapter.in.web.dto.GuardianResponse;
import com.dershane.userservice.user.adapter.in.web.dto.LinkGuardianRequest;
import com.dershane.userservice.user.adapter.in.web.dto.StudentGuardianResponse;
import com.dershane.userservice.user.adapter.in.web.dto.StudentResponse;
import com.dershane.userservice.user.adapter.in.web.dto.TeacherResponse;
import com.dershane.userservice.user.adapter.in.web.dto.TemporaryPasswordResponse;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateAdminRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateGuardianRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateStudentRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateTeacherRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UserResponse;
import com.dershane.userservice.user.application.port.in.GuardianChildView;
import com.dershane.userservice.user.application.port.in.GuardianView;
import com.dershane.userservice.user.application.port.in.LinkGuardianCommand;
import com.dershane.userservice.user.application.port.in.RegisterAdminCommand;
import com.dershane.userservice.user.application.port.in.RegisterGuardianCommand;
import com.dershane.userservice.user.application.port.in.RegisterStudentCommand;
import com.dershane.userservice.user.application.port.in.RegisterTeacherCommand;
import com.dershane.userservice.user.application.port.in.StudentGuardianView;
import com.dershane.userservice.user.application.port.in.StudentView;
import com.dershane.userservice.user.application.port.in.TeacherView;
import com.dershane.userservice.user.application.port.in.TemporaryPasswordResult;
import com.dershane.userservice.user.application.port.in.UpdateAdminCommand;
import com.dershane.userservice.user.application.port.in.UpdateGuardianCommand;
import com.dershane.userservice.user.application.port.in.UpdateStudentCommand;
import com.dershane.userservice.user.application.port.in.UpdateTeacherCommand;
import com.dershane.userservice.user.application.port.in.UserView;
import org.mapstruct.Mapper;

/** Request records to commands, views to response records. */
@Mapper
interface UserWebMapper {

  RegisterStudentCommand toCommand(CreateStudentRequest request);

  UpdateStudentCommand toCommand(UpdateStudentRequest request);

  RegisterTeacherCommand toCommand(CreateTeacherRequest request);

  UpdateTeacherCommand toCommand(UpdateTeacherRequest request);

  RegisterGuardianCommand toCommand(CreateGuardianRequest request);

  UpdateGuardianCommand toCommand(UpdateGuardianRequest request);

  RegisterAdminCommand toCommand(CreateAdminRequest request);

  UpdateAdminCommand toCommand(UpdateAdminRequest request);

  LinkGuardianCommand toCommand(LinkGuardianRequest request);

  UserResponse toResponse(UserView view);

  StudentResponse toResponse(StudentView view);

  TeacherResponse toResponse(TeacherView view);

  GuardianResponse toResponse(GuardianView view);

  StudentGuardianResponse toResponse(StudentGuardianView view);

  GuardianChildResponse toResponse(GuardianChildView view);

  TemporaryPasswordResponse toResponse(TemporaryPasswordResult result);
}
