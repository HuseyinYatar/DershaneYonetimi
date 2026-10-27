package com.dershane.userservice.enrollment.adapter.in.web;

import com.dershane.userservice.enrollment.adapter.in.web.dto.ClassroomStudentResponse;
import com.dershane.userservice.enrollment.adapter.in.web.dto.ClassroomTeacherResponse;
import com.dershane.userservice.enrollment.application.port.in.ListClassroomStudentsQuery;
import com.dershane.userservice.enrollment.application.port.in.ListClassroomTeachersQuery;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Classrooms")
@RestController
@RequestMapping("/api/v1/classrooms/{classroomId}")
@RequiredArgsConstructor
class ClassroomRosterController {

  private final ListClassroomStudentsQuery listStudents;
  private final ListClassroomTeachersQuery listTeachers;
  private final EnrollmentWebMapper mapper;

  @Operation(summary = "Current students of a classroom (admins and its teachers)")
  @GetMapping("/students")
  public List<ClassroomStudentResponse> students(
      @CurrentActor Actor actor, @PathVariable UUID classroomId) {
    return listStudents.students(actor, classroomId).stream().map(mapper::toResponse).toList();
  }

  @Operation(summary = "Teachers and subjects of a classroom")
  @GetMapping("/teachers")
  public List<ClassroomTeacherResponse> teachers(
      @CurrentActor Actor actor, @PathVariable UUID classroomId) {
    return listTeachers.teachers(actor, classroomId).stream().map(mapper::toResponse).toList();
  }
}
