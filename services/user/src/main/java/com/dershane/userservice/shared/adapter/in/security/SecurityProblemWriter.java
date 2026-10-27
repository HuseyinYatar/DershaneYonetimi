package com.dershane.userservice.shared.adapter.in.security;

import com.dershane.userservice.shared.adapter.in.web.ErrorStatusMapper;
import com.dershane.userservice.shared.adapter.in.web.ProblemDetailFactory;
import com.dershane.userservice.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/** Writes a ProblemDetail body for 401/403 responses produced by the security filter chain. */
@Component
@RequiredArgsConstructor
public class SecurityProblemWriter {

  private final ProblemDetailFactory problems;
  private final LocaleResolver localeResolver;
  private final JsonMapper jsonMapper;

  public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
      throws IOException {
    ProblemDetail problem = problems.create(code, localeResolver.resolveLocale(request));
    // Same fields as MVC problem responses; an absent "type" means "about:blank" (RFC 9457).
    Map<String, Object> body = new LinkedHashMap<>();
    if (problem.getType() != null) {
      body.put("type", problem.getType().toString());
    }
    body.put("title", problem.getTitle());
    body.put("status", problem.getStatus());
    body.put("detail", problem.getDetail());
    body.put(ProblemDetailFactory.ERROR_CODE_PROPERTY, code.name());
    response.setStatus(ErrorStatusMapper.statusFor(code).value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    jsonMapper.writeValue(response.getOutputStream(), body);
  }
}
