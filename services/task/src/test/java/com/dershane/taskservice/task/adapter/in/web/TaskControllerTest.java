package com.dershane.taskservice.task.adapter.in.web;

import static com.dershane.taskservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.taskservice.shared.domain.DomainException;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.support.WebMvcTestSupport;
import com.dershane.taskservice.task.application.port.in.CreateTaskUseCase;
import com.dershane.taskservice.task.application.port.in.DeactivateTaskUseCase;
import com.dershane.taskservice.task.application.port.in.GetTaskQuery;
import com.dershane.taskservice.task.application.port.in.ListTasksQuery;
import com.dershane.taskservice.task.application.port.in.TaskView;
import com.dershane.taskservice.task.application.port.in.UpdateTaskUseCase;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@Import({WebMvcTestSupport.class, TaskWebMapperImpl.class})
class TaskControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private CreateTaskUseCase createTask;
  @MockitoBean private UpdateTaskUseCase updateTask;
  @MockitoBean private DeactivateTaskUseCase deactivateTask;
  @MockitoBean private GetTaskQuery getTask;
  @MockitoBean private ListTasksQuery listTasks;

  private final UUID branchId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();

  private String body() {
    return "{\"title\":\"Problems\",\"description\":\"Solve\",\"targetType\":\"CLASSROOM\","
        + "\"targetId\":\""
        + classroomId
        + "\",\"recurrence\":\"DAILY\",\"startDate\":\"2026-11-09\",\"dueTime\":\"20:00:00\"}";
  }

  private TaskView view() {
    return new TaskView(
        taskId,
        branchId,
        UUID.randomUUID(),
        "Problems",
        "Solve",
        TargetType.CLASSROOM,
        classroomId,
        Recurrence.DAILY,
        Set.of(),
        LocalDate.of(2026, 11, 9),
        null,
        LocalTime.of(20, 0),
        List.of(),
        true,
        Instant.now());
  }

  @Test
  void onlyTeachersCreateTasks() throws Exception {
    for (String role : new String[] {"student", "guardian", "admin"}) {
      mvc.perform(
              post("/api/v1/tasks")
                  .with(token(UUID.randomUUID(), branchId, role))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
    verifyNoInteractions(createTask);
  }

  @Test
  void aTeacherCreatesATaskAndGetsItsLocation() throws Exception {
    when(createTask.create(any(), any())).thenReturn(view());

    mvc.perform(
            post("/api/v1/tasks")
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/tasks/" + taskId))
        .andExpect(jsonPath("$.recurrence").value("DAILY"))
        .andExpect(jsonPath("$.title").value("Problems"));
  }

  @Test
  void aMissingTitleIsABadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/tasks")
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"targetType\":\"CLASSROOM\",\"targetId\":\""
                        + classroomId
                        + "\",\"recurrence\":\"DAILY\",\"startDate\":\"2026-11-09\","
                        + "\"dueTime\":\"20:00:00\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(createTask);
  }

  @Test
  void aRuleViolationIsReportedWithItsCodeAndATurkishMessage() throws Exception {
    when(createTask.create(any(), any()))
        .thenThrow(new DomainException(ErrorCode.TARGET_NOT_IN_BRANCH));

    mvc.perform(
            post("/api/v1/tasks")
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_IN_BRANCH"))
        .andExpect(jsonPath("$.detail").value("Öğrenci veya sınıf şubenizde aktif değil."));
  }

  @Test
  void studentsAndGuardiansCannotListTasks() throws Exception {
    for (String role : new String[] {"student", "guardian"}) {
      mvc.perform(get("/api/v1/tasks").with(token(UUID.randomUUID(), branchId, role)))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(listTasks);
  }

  @Test
  void aTeacherListsTasksAsAPage() throws Exception {
    when(listTasks.list(any(), any(), any(), any(), any())).thenReturn(Page.empty());

    mvc.perform(
            get("/api/v1/tasks")
                .param("active", "true")
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void anAdminDeactivatesATask() throws Exception {
    when(deactivateTask.deactivate(any(), any())).thenReturn(view());

    mvc.perform(
            post("/api/v1/tasks/{id}/deactivate", taskId)
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isOk());
  }

  @Test
  void studentsCannotDeactivateTasks() throws Exception {
    mvc.perform(
            post("/api/v1/tasks/{id}/deactivate", taskId)
                .with(token(UUID.randomUUID(), branchId, "student")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(deactivateTask, updateTask, getTask);
  }
}
