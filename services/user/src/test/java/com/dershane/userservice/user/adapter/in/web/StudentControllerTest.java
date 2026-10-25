package com.dershane.userservice.user.adapter.in.web;

import static com.dershane.userservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.support.WebMvcTestSupport;
import com.dershane.userservice.user.application.port.in.GetStudentQuery;
import com.dershane.userservice.user.application.port.in.ListStudentsQuery;
import com.dershane.userservice.user.application.port.in.RegisterStudentUseCase;
import com.dershane.userservice.user.application.port.in.RegistrationResult;
import com.dershane.userservice.user.application.port.in.StudentView;
import com.dershane.userservice.user.application.port.in.UpdateStudentUseCase;
import com.dershane.userservice.user.domain.TargetExam;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentController.class)
@Import({WebMvcTestSupport.class, UserWebMapperImpl.class})
class StudentControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private RegisterStudentUseCase registerStudent;
  @MockitoBean private UpdateStudentUseCase updateStudent;
  @MockitoBean private GetStudentQuery getStudent;
  @MockitoBean private ListStudentsQuery listStudents;

  private final UUID branchId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();

  private String body() {
    return """
        {"branchId":"%s","firstName":"Ali","lastName":"Kaya","studentNumber":"1042",
         "gradeLevel":8,"targetExam":"LGS"}
        """
        .formatted(branchId);
  }

  @Test
  void teacherCannotRegisterStudents() throws Exception {
    mvc.perform(
            post("/api/v1/students")
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    verifyNoInteractions(registerStudent);
  }

  @Test
  void studentWithoutEmailGetsTemporaryPasswordOnce() throws Exception {
    when(registerStudent.register(any(), any()))
        .thenReturn(new RegistrationResult<>(student(), false, "Tmp2345abcde"));

    mvc.perform(
            post("/api/v1/students")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.username").value("mrkz-1042"))
        .andExpect(jsonPath("$.temporaryPassword").value("Tmp2345abcde"))
        .andExpect(jsonPath("$.passwordSetupEmailSent").value(false))
        .andExpect(jsonPath("$.temporaryPasswordAlreadyIssued").value(false));
  }

  @Test
  void gradeLevelOutOfRangeIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/students")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body().replace("\"gradeLevel\":8", "\"gradeLevel\":14")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("gradeLevel"));
    verifyNoInteractions(registerStudent);
  }

  @Test
  void otherBranchIsReportedAsBranchScopeViolation() throws Exception {
    when(getStudent.get(any(), eq(studentId)))
        .thenThrow(new DomainException(ErrorCode.BRANCH_SCOPE_VIOLATION));

    mvc.perform(
            get("/api/v1/students/{id}", studentId)
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("BRANCH_SCOPE_VIOLATION"))
        .andExpect(jsonPath("$.detail").value("Yalnızca kendi şubenizi yönetebilirsiniz."));
  }

  @Test
  void identityProviderOutageIsA503() throws Exception {
    when(registerStudent.register(any(), any()))
        .thenThrow(new DomainException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE));

    mvc.perform(
            post("/api/v1/students")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("IDENTITY_PROVIDER_UNAVAILABLE"));
  }

  @Test
  void studentListNoLongerAcceptsAClassroomFilter() throws Exception {
    when(listStudents.list(any(), any(), any(), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(student())));

    mvc.perform(
            get("/api/v1/students")
                .param("classroomId", UUID.randomUUID().toString())
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  private StudentView student() {
    return new StudentView(
        studentId,
        branchId,
        "Ali",
        "Kaya",
        null,
        null,
        "mrkz-1042",
        true,
        "1042",
        8,
        TargetExam.LGS,
        Instant.now(),
        Instant.now());
  }
}
