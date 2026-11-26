package com.dershane.userservice.enrollment.adapter.in.web;

import static com.dershane.userservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.userservice.enrollment.application.port.in.BranchAssignmentView;
import com.dershane.userservice.enrollment.application.port.in.ListBranchTeacherAssignmentsQuery;
import com.dershane.userservice.support.WebMvcTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BranchTeacherAssignmentController.class)
@Import({WebMvcTestSupport.class, EnrollmentWebMapperImpl.class})
class BranchTeacherAssignmentControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private ListBranchTeacherAssignmentsQuery listAssignments;

  private final UUID branchId = UUID.randomUUID();

  @Test
  void adminListsAssignmentsWithWeeklyHours() throws Exception {
    UUID assignmentId = UUID.randomUUID();
    UUID teacherId = UUID.randomUUID();
    UUID classroomId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();
    when(listAssignments.listOfBranch(any(), eq(branchId)))
        .thenReturn(
            List.of(
                new BranchAssignmentView(
                    assignmentId, teacherId, classroomId, "12-A", subjectId, "Geometri", 4)));

    mvc.perform(
            get("/api/v1/branches/{id}/teacher-assignments", branchId)
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].assignmentId").value(assignmentId.toString()))
        .andExpect(jsonPath("$[0].classroomName").value("12-A"))
        .andExpect(jsonPath("$[0].subjectName").value("Geometri"))
        .andExpect(jsonPath("$[0].weeklyHours").value(4));
  }

  @Test
  void teacherCannotListBranchAssignments() throws Exception {
    mvc.perform(
            get("/api/v1/branches/{id}/teacher-assignments", branchId)
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    verifyNoInteractions(listAssignments);
  }
}
