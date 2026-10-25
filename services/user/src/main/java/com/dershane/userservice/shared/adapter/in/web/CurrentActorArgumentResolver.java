package com.dershane.userservice.shared.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.security.ActorProvider;
import com.dershane.userservice.shared.application.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@code @CurrentActor Actor} controller parameters from the validated JWT. */
@Component
@RequiredArgsConstructor
public class CurrentActorArgumentResolver implements HandlerMethodArgumentResolver {

  private final ActorProvider actorProvider;

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentActor.class)
        && Actor.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    return actorProvider.require();
  }
}
