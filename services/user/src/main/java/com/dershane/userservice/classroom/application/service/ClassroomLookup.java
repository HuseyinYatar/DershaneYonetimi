package com.dershane.userservice.classroom.application.service;

import com.dershane.userservice.classroom.application.port.in.LoadClassroomQuery;
import com.dershane.userservice.classroom.application.port.out.LoadClassroomPort;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class ClassroomLookup implements LoadClassroomQuery {

  private final LoadClassroomPort classrooms;

  @Override
  @Transactional(readOnly = true)
  public Classroom require(UUID classroomId) {
    return classrooms
        .findById(classroomId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.CLASSROOM_NOT_FOUND,
                    "Classroom not found",
                    "classroomId",
                    classroomId));
  }
}
