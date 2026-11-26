package com.dershane.attendanceservice.schedule.adapter.out.userdirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dershane.attendanceservice.schedule.application.port.out.UserDirectoryPort.BranchAssignment;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UserDirectoryRestAdapterTest {

  private static final String BASE = "http://user-service";

  private final UUID branchId = UUID.randomUUID();
  private MockRestServiceServer server;
  private UserDirectoryRestAdapter adapter;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    adapter = new UserDirectoryRestAdapter(builder.build());
    Jwt jwt =
        new Jwt(
            "caller-token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", UUID.randomUUID().toString()));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void readsTheAssignmentsWithTheCallersOwnToken() {
    UUID teacher = UUID.randomUUID();
    UUID classroom = UUID.randomUUID();
    UUID subject = UUID.randomUUID();
    server
        .expect(requestTo(BASE + "/api/v1/branches/" + branchId + "/teacher-assignments"))
        .andExpect(header("Authorization", "Bearer caller-token"))
        .andRespond(
            withSuccess(
                "[{\"assignmentId\":\""
                    + UUID.randomUUID()
                    + "\",\"teacherId\":\""
                    + teacher
                    + "\",\"classroomId\":\""
                    + classroom
                    + "\",\"classroomName\":\"12-A\","
                    + "\"subjectId\":\""
                    + subject
                    + "\",\"subjectName\":\"Geometri\","
                    + "\"weeklyHours\":4}]",
                MediaType.APPLICATION_JSON));

    List<BranchAssignment> assignments = adapter.loadBranchAssignments(branchId);

    assertThat(assignments).containsExactly(new BranchAssignment(teacher, classroom, subject, 4));
    server.verify();
  }

  @Test
  void aFailingUserServiceIsReportedAsUnavailable() {
    server
        .expect(requestTo(BASE + "/api/v1/branches/" + branchId + "/teacher-assignments"))
        .andRespond(withServerError());

    DomainException thrown = thrownBy(() -> adapter.loadBranchAssignments(branchId));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.USER_SERVICE_UNAVAILABLE);
  }

  @Test
  void aRefusalFromTheUserServiceIsAlsoUnavailableNotAForgedSuccess() {
    server
        .expect(requestTo(BASE + "/api/v1/branches/" + branchId + "/teacher-assignments"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    DomainException thrown = thrownBy(() -> adapter.loadBranchAssignments(branchId));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.USER_SERVICE_UNAVAILABLE);
  }

  @Test
  void withoutABearerTokenNothingIsSent() {
    SecurityContextHolder.clearContext();

    DomainException thrown = thrownBy(() -> adapter.loadBranchAssignments(branchId));

    assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    server.verify();
  }

  private static DomainException thrownBy(Runnable action) {
    try {
      action.run();
    } catch (DomainException ex) {
      return ex;
    }
    throw new AssertionError("expected a DomainException");
  }
}
