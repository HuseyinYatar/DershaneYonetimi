package com.dershane.userservice.enrollment.adapter.in.web;

import com.dershane.userservice.enrollment.adapter.in.web.dto.MyClassroomResponse;
import com.dershane.userservice.enrollment.application.port.in.ListMyClassroomsQuery;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me")
@RestController
@RequestMapping("/api/v1/me/classrooms")
@RequiredArgsConstructor
class MyClassroomsController {

  private final ListMyClassroomsQuery listMyClassrooms;
  private final EnrollmentWebMapper mapper;

  @Operation(summary = "Teachers: classrooms they teach. Students: their current classroom")
  @GetMapping
  public List<MyClassroomResponse> classrooms(@CurrentActor Actor actor) {
    return listMyClassrooms.myClassrooms(actor).stream().map(mapper::toResponse).toList();
  }
}
