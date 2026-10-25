package com.dershane.userservice.shared.adapter.in.security;

import com.dershane.userservice.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 raised by the filter chain (URL rules). Method-level 403s go through the MVC handler. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingAccessDeniedHandler implements AccessDeniedHandler {

  private final SecurityProblemWriter problemWriter;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.ACCESS_DENIED.name())
        .addKeyValue("http.method", request.getMethod())
        .log("Request rejected by URL authorization rules");
    problemWriter.write(request, response, ErrorCode.ACCESS_DENIED);
  }
}
