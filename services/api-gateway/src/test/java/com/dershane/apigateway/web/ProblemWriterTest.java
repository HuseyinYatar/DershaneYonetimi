package com.dershane.apigateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class ProblemWriterTest {

  @ParameterizedTest
  @CsvSource({
    "'en-US,en;q=0.9', en",
    "'en', en",
    "'tr-TR', tr",
    "'de-DE,de;q=0.9', tr",
    "'*', tr",
    "';;;not-a-language', tr"
  })
  void picksEnglishOnlyWhenRequested(String acceptLanguage, String expected) {
    var request = MockServerHttpRequest.get("/x").header("Accept-Language", acceptLanguage).build();

    assertThat(ProblemWriter.localeOf(request).getLanguage()).isEqualTo(expected);
  }

  @Test
  void defaultsToTurkishWithoutHeader() {
    var request = MockServerHttpRequest.get("/x").build();

    assertThat(ProblemWriter.localeOf(request)).isEqualTo(Locale.forLanguageTag("tr"));
  }
}
