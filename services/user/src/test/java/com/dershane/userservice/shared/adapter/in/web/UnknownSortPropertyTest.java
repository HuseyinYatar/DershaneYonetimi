package com.dershane.userservice.shared.adapter.in.web;

import static com.dershane.userservice.support.TestJwts.token;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.userservice.support.WebMvcTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(UnknownSortPropertyTest.ThrowingController.class)
@Import({WebMvcTestSupport.class, UnknownSortPropertyTest.ThrowingController.class})
class UnknownSortPropertyTest {

  @Autowired private MockMvc mvc;

  @Test
  void anUnknownSortPropertyIsABadRequestNotAServerError() throws Exception {
    mvc.perform(
            get("/api/v1/unknown-sort-probe")
                .with(token(UUID.randomUUID(), UUID.randomUUID(), "teacher")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/api/v1/unknown-sort-probe")
    String probe() {
      throw new PropertyReferenceException("nope", TypeInformation.of(Object.class), List.of());
    }
  }
}
