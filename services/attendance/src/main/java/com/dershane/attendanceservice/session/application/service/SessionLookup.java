package com.dershane.attendanceservice.session.application.service;

import com.dershane.attendanceservice.session.application.port.in.LoadSessionQuery;
import com.dershane.attendanceservice.session.application.port.out.LoadSessionPort;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class SessionLookup implements LoadSessionQuery {

  private final LoadSessionPort sessions;

  @Override
  @Transactional(readOnly = true)
  public ClassSession require(UUID sessionId) {
    return sessions
        .findById(sessionId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.SESSION_NOT_FOUND,
                    "Class session not found",
                    "sessionId",
                    sessionId));
  }
}
