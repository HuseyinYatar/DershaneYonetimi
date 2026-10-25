package com.dershane.userservice.shared.adapter.in.web;

import com.dershane.userservice.shared.domain.ErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Creates RFC 9457 problem details with an {@code errorCode} and a localized {@code detail}. */
@Component
@RequiredArgsConstructor
public class ProblemDetailFactory {

  public static final String ERROR_CODE_PROPERTY = "errorCode";

  private final MessageSource messageSource;

  public ProblemDetail create(ErrorCode errorCode) {
    return create(errorCode, LocaleContextHolder.getLocale());
  }

  public ProblemDetail create(ErrorCode errorCode, Locale locale) {
    String detail =
        messageSource.getMessage(errorCode.messageKey(), null, errorCode.name(), locale);
    HttpStatus status = ErrorStatusMapper.statusFor(errorCode);
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty(ERROR_CODE_PROPERTY, errorCode.name());
    return problem;
  }
}
