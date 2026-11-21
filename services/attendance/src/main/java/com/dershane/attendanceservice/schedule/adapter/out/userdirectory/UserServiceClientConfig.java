package com.dershane.attendanceservice.schedule.adapter.out.userdirectory;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client for the user service: it fails fast, so a slow user service cannot hold requests.
 */
@Configuration(proxyBeanMethods = false)
class UserServiceClientConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  @Bean
  RestClient userServiceRestClient(UserServiceProperties properties) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().baseUrl(properties.url()).requestFactory(factory).build();
  }
}
