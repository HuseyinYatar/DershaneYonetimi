package com.dershane.userservice.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

  private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

  @RepeatedTest(20)
  void containsUpperLowerAndDigitWithoutAmbiguousCharacters() {
    String password = generator.generate();

    assertThat(password).hasSize(12);
    assertThat(password).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*[2-9].*");
    assertThat(password).doesNotContainPattern("[0O1lI]");
  }

  @Test
  void passwordsAreNotRepeated() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      seen.add(generator.generate());
    }
    assertThat(seen).hasSize(1000);
  }
}
