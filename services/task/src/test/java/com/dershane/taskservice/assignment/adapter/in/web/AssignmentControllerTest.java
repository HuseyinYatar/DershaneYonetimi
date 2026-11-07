package com.dershane.taskservice.assignment.adapter.in.web;

import static com.dershane.taskservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.taskservice.assignment.application.port.in.AssignmentDetailView;
import com.dershane.taskservice.assignment.application.port.in.EvaluateAssignmentUseCase;
import com.dershane.taskservice.assignment.application.port.in.GetAssignmentQuery;
import com.dershane.taskservice.assignment.application.port.in.ListAssignmentsQuery;
import com.dershane.taskservice.assignment.application.port.in.SubmissionView;
import com.dershane.taskservice.assignment.application.port.in.SubmitAssignmentUseCase;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.shared.domain.DomainException;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.support.WebMvcTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AssignmentController.class)
@Import({WebMvcTestSupport.class, AssignmentWebMapperImpl.class})
class AssignmentControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private ListAssignmentsQuery listAssignments;
  @MockitoBean private GetAssignmentQuery getAssignment;
  @MockitoBean private SubmitAssignmentUseCase submitAssignment;
  @MockitoBean private EvaluateAssignmentUseCase evaluateAssignment;

  private final UUID branchId = UUID.randomUUID();
  private final UUID assignmentId = UUID.randomUUID();

  private AssignmentDetailView view(AssignmentStatus status) {
    Instant now = Instant.parse("2026-11-09T08:00:00Z");
    return new AssignmentDetailView(
        assignmentId,
        UUID.randomUUID(),
        "Problems",
        "Solve",
        List.of(),
        UUID.randomUUID(),
        branchId,
        UUID.randomUUID(),
        LocalDate.of(2026, 11, 9),
        now.plusSeconds(3600),
        status,
        now,
        status == AssignmentStatus.SUBMITTED
            ? new SubmissionView("Done", List.of(), now, false)
            : null,
        null);
  }

  @Test
  void onlyStudentsSubmit() throws Exception {
    for (String role : new String[] {"teacher", "guardian", "admin"}) {
      mvc.perform(
              put("/api/v1/task-assignments/{id}/submission", assignmentId)
                  .with(token(UUID.randomUUID(), branchId, role))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"description\":\"Done\"}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(submitAssignment);
  }

  @Test
  void aStudentSubmits() throws Exception {
    when(submitAssignment.submit(any(), any(), any())).thenReturn(view(AssignmentStatus.SUBMITTED));

    mvc.perform(
            put("/api/v1/task-assignments/{id}/submission", assignmentId)
                .with(token(UUID.randomUUID(), branchId, "student"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Done\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.submission.description").value("Done"));
  }

  @Test
  void onlyTeachersEvaluate() throws Exception {
    for (String role : new String[] {"student", "guardian", "admin"}) {
      mvc.perform(
              put("/api/v1/task-assignments/{id}/evaluation", assignmentId)
                  .with(token(UUID.randomUUID(), branchId, role))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"score\":80}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(evaluateAssignment);
  }

  @Test
  void aScoreOutOfRangeIsABadRequest() throws Exception {
    mvc.perform(
            put("/api/v1/task-assignments/{id}/evaluation", assignmentId)
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":101}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(evaluateAssignment);
  }

  @Test
  void anEvaluationRuleViolationKeepsItsCode() throws Exception {
    when(evaluateAssignment.evaluate(any(), any(), any()))
        .thenThrow(new DomainException(ErrorCode.NOT_SUBMITTED));

    mvc.perform(
            put("/api/v1/task-assignments/{id}/evaluation", assignmentId)
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":80}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("NOT_SUBMITTED"));
  }

  @Test
  void listingIsOpenToEveryRoleAndReturnsAPage() throws Exception {
    when(listAssignments.list(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

    for (String role : new String[] {"student", "guardian", "teacher", "admin"}) {
      mvc.perform(
              get("/api/v1/task-assignments")
                  .param("from", "2026-11-01")
                  .with(token(UUID.randomUUID(), branchId, role)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray());
    }
  }

  @Test
  void aDetailIsReturnedToAnAllowedCaller() throws Exception {
    when(getAssignment.get(any(), any())).thenReturn(view(AssignmentStatus.ASSIGNED));

    mvc.perform(
            get("/api/v1/task-assignments/{id}", assignmentId)
                .with(token(UUID.randomUUID(), branchId, "student")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskTitle").value("Problems"));
  }

  @Test
  void theAssignmentsOfATaskAreForStaffOnly() throws Exception {
    mvc.perform(
            get("/api/v1/tasks/{id}/assignments", UUID.randomUUID())
                .with(token(UUID.randomUUID(), branchId, "student")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(listAssignments);
  }

  @Test
  void anUnauthenticatedCallIsRejected() throws Exception {
    mvc.perform(get("/api/v1/task-assignments")).andExpect(status().isUnauthorized());
  }
}
