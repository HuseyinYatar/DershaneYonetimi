package com.dershane.userservice.user.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UpdateRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private static UpdateStudentRequest student(String firstName, String lastName, String phone) {
    return new UpdateStudentRequest(firstName, lastName, null, phone, null, null, null);
  }

  private static Set<String> invalidFields(Set<ConstraintViolation<UpdateStudentRequest>> found) {
    return found.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(java.util.stream.Collectors.toSet());
  }

  @Test
  void aNameOfBlanksOnlyIsRejectedSoItCannotBlankOutTheStoredName() {
    assertThat(invalidFields(validator.validate(student("   ", "\t", null))))
        .containsExactlyInAnyOrder("firstName", "lastName");
  }

  @Test
  void aMissingNameStaysAllowedBecauseAPatchLeavesItUnchanged() {
    assertThat(validator.validate(student(null, null, null))).isEmpty();
  }

  @Test
  void aPhoneNeedsAtLeastTenDigitsNotJustSeparators() {
    assertThat(invalidFields(validator.validate(student(null, null, "----------"))))
        .containsExactly("phone");
    assertThat(invalidFields(validator.validate(student(null, null, "---------1"))))
        .containsExactly("phone");
    assertThat(validator.validate(student(null, null, "05551112233"))).isEmpty();
    assertThat(validator.validate(student(null, null, "+90 (555) 111-22-33"))).isEmpty();
  }
}
