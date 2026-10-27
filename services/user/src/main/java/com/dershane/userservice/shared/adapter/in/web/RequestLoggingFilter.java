package com.dershane.userservice.shared.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Writes one log line per request after it completes: method, route template, status, duration.
 *
 * <p>The route template ({@code /api/v1/students/{id}}) is logged instead of the raw URI, and the
 * query string and body are never logged, so no personal data reaches the logs. It runs after the
 * tracing filter, so the line carries traceId and spanId.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final String UNMATCHED_ROUTE = "UNMATCHED";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - start) / 1_000_000;
      Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      log.atInfo()
          .addKeyValue("http.method", request.getMethod())
          .addKeyValue("http.route", route == null ? UNMATCHED_ROUTE : route.toString())
          .addKeyValue("http.status", response.getStatus())
          .addKeyValue("duration_ms", durationMs)
          .log("HTTP request completed");
    }
  }
}
