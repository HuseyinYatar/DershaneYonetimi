package com.dershane.userservice.enrollment.application.port.out;

import com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomTeacherView;
import com.dershane.userservice.enrollment.application.port.in.TeacherAssignmentView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read side of teacher assignments. Joins the classroom, subject and teacher tables by name. */
public interface TeacherAssignmentQueryPort {

  Optional<TeacherAssignmentView> findViewById(UUID assignmentId);

  List<TeacherAssignmentView> findAssignmentsOfTeacher(UUID teacherId);

  List<ClassroomTeacherView> findTeachersOfClassroom(UUID classroomId);

  /** Active classrooms the teacher teaches, newest term first. */
  List<ClassroomSummaryView> findActiveClassroomsOfTeacher(UUID teacherId);
}
