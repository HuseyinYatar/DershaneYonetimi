package com.dershane.attendanceservice.schedule.adapter.out.userdirectory;

import com.dershane.attendanceservice.schedule.application.port.out.UserDirectoryPort;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads a branch's teacher assignments from the user service with the caller's own token, so the
 * user service decides who may see them. Any failure is a 503 for the caller; nothing about the
 * response (it carries class and subject names) is logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UserDirectoryRestAdapter implements UserDirectoryPort {

  /** The fields of the user service's branch assignment list that the timetable uses. */
  private record Item(
      UUID assignmentId,
      UUID teacherId,
      UUID classroomId,
      String classroomName,
      UUID subjectId,
      String subjectName,
      int weeklyHours) {}

  private final RestClient userServiceRestClient;

  @Override
  public List<BranchAssignment> loadBranchAssignments(UUID branchId) {
    String token = bearerToken(branchId);
    try {
      List<Item> items =
          userServiceRestClient
              .get()
              .uri("/api/v1/branches/{branchId}/teacher-assignments", branchId)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
              .retrieve()
              .body(new ParameterizedTypeReference<List<Item>>() {});
      return (items == null ? List.<Item>of() : items)
          .stream()
              .map(
                  i ->
                      new BranchAssignment(
                          i.teacherId(), i.classroomId(), i.subjectId(), i.weeklyHours()))
              .toList();
    } catch (RestClientException ex) {
      String status =
          ex instanceof RestClientResponseException response
              ? String.valueOf(response.getStatusCode().value())
              : "none";
      throw Rejections.reject(
          log,
          ErrorCode.USER_SERVICE_UNAVAILABLE,
          "User service call failed",
          "branchId",
          branchId,
          "status",
          status,
          "reason",
          ex.getClass().getSimpleName());
    }
  }

  private static String bearerToken(UUID branchId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt) {
      return jwt.getToken().getTokenValue();
    }
    throw Rejections.reject(
        log,
        ErrorCode.AUTHENTICATION_REQUIRED,
        "No bearer token to forward to the user service",
        "branchId",
        branchId);
  }
}
