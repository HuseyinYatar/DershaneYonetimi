package com.dershane.userservice.enrollment.application.port.out;

import com.dershane.userservice.enrollment.domain.TeacherAssignment;

public interface SaveTeacherAssignmentPort {

  TeacherAssignment save(TeacherAssignment assignment);

  void delete(TeacherAssignment assignment);
}
