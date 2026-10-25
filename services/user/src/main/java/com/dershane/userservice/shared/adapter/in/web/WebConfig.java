package com.dershane.userservice.shared.adapter.in.web;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@EnableSpringDataWebSupport(
    pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

  private final CurrentActorArgumentResolver currentActorArgumentResolver;

  /** User-facing error messages are Turkish unless the client explicitly asks for English. */
  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    Locale turkish = Locale.forLanguageTag("tr");
    resolver.setDefaultLocale(turkish);
    resolver.setSupportedLocales(List.of(turkish, Locale.ENGLISH));
    return resolver;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentActorArgumentResolver);
  }
}
