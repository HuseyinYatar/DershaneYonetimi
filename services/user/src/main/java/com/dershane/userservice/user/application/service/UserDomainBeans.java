package com.dershane.userservice.user.application.service;

import com.dershane.userservice.user.domain.TemporaryPasswordGenerator;
import com.dershane.userservice.user.domain.UsernamePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The domain has no Spring annotations; the application layer registers its stateless services. */
@Configuration(proxyBeanMethods = false)
class UserDomainBeans {

  @Bean
  UsernamePolicy usernamePolicy() {
    return new UsernamePolicy();
  }

  @Bean
  TemporaryPasswordGenerator temporaryPasswordGenerator() {
    return new TemporaryPasswordGenerator();
  }
}
