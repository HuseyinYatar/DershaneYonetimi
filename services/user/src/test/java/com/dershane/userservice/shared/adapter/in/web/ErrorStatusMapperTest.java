package com.dershane.userservice.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.userservice.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/** The error codes keep the HTTP statuses clients already depend on. */
class ErrorStatusMapperTest {

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void everyErrorCodeHasAStatus(ErrorCode code) {
    assertThat(ErrorStatusMapper.statusFor(code)).isNotNull();
  }

  @Test
  void categoriesMapToTheOriginalStatuses() {
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.VALIDATION_FAILED))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.MALFORMED_REQUEST))
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.AUTHENTICATION_REQUIRED))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.ACCESS_DENIED))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.BRANCH_SCOPE_VIOLATION))
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.STUDENT_NOT_FOUND))
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.METHOD_NOT_ALLOWED))
        .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.STUDENT_NUMBER_TAKEN))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.CONCURRENT_MODIFICATION))
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.GRADE_LEVEL_MISMATCH))
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.IDEMPOTENCY_KEY_REUSED))
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.IDENTITY_PROVIDER_ERROR))
        .isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(ErrorStatusMapper.statusFor(ErrorCode.INTERNAL_ERROR))
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
