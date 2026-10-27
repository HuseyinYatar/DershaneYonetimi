package com.dershane.userservice.shared.adapter.in.security;

import com.dershane.userservice.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401: missing, expired or invalid token. Keeps the standard WWW-Authenticate header. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final BearerTokenAuthenticationEntryPoint delegate =
      new BearerTokenAuthenticationEntryPoint();
  private final SecurityProblemWriter problemWriter;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.AUTHENTICATION_REQUIRED.name())
        .addKeyValue("reason", authException.getClass().getSimpleName())
        .addKeyValue("http.method", request.getMethod())
        .log("Request rejected: authentication failed");
    delegate.commence(request, response, authException);
    problemWriter.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED);
  }
}
