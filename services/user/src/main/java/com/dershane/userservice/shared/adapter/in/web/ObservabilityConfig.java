package com.dershane.userservice.shared.adapter.in.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

  /**
   * Boot's HTTP observation filter runs at {@code HIGHEST_PRECEDENCE + 1}; running right after it
   * puts the trace context into the log line, and running before Spring Security means rejected
   * (401/403) requests are logged too.
   */
  @Bean
  public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
    FilterRegistrationBean<RequestLoggingFilter> registration =
        new FilterRegistrationBean<>(new RequestLoggingFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    registration.addUrlPatterns("/*");
    return registration;
  }
}
