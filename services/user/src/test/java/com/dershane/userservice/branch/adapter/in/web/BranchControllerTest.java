package com.dershane.userservice.branch.adapter.in.web;

import static com.dershane.userservice.support.TestJwts.token;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.userservice.branch.application.port.in.BranchView;
import com.dershane.userservice.branch.application.port.in.CreateBranchUseCase;
import com.dershane.userservice.branch.application.port.in.DeactivateBranchUseCase;
import com.dershane.userservice.branch.application.port.in.GetBranchQuery;
import com.dershane.userservice.branch.application.port.in.ListBranchesQuery;
import com.dershane.userservice.branch.application.port.in.UpdateBranchUseCase;
import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.support.WebMvcTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BranchController.class)
@Import({WebMvcTestSupport.class, BranchWebMapperImpl.class})
class BranchControllerTest {

  private static final String VALID_BODY = "{\"name\":\"Kadıköy Şubesi\",\"code\":\"kdk\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private CreateBranchUseCase createBranch;
  @MockitoBean private UpdateBranchUseCase updateBranch;
  @MockitoBean private DeactivateBranchUseCase deactivateBranch;
  @MockitoBean private GetBranchQuery getBranch;
  @MockitoBean private ListBranchesQuery listBranches;

  private final UUID branchId = UUID.randomUUID();

  @Test
  void requestWithoutTokenGets401ProblemWithBearerChallenge() throws Exception {
    mvc.perform(
            post("/api/v1/branches").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
        .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.detail").value("Lütfen giriş yapın."));
    verifyNoInteractions(createBranch);
  }

  @Test
  void branchAdminCannotCreateBranches() throws Exception {
    mvc.perform(
            post("/api/v1/branches")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    verifyNoInteractions(createBranch);
  }

  @Test
  void invalidBodyReturnsFieldErrorsInTurkish() throws Exception {
    mvc.perform(
            post("/api/v1/branches")
                .with(token(UUID.randomUUID(), branchId, "admin", "super-admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"code\":\"x!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.detail").value("İstekte geçersiz alanlar var."))
        .andExpect(jsonPath("$.errors.length()").value(2));
  }

  @Test
  void superAdminCreatesBranch() throws Exception {
    when(createBranch.create(any()))
        .thenReturn(
            new BranchView(branchId, "Kadıköy Şubesi", "KDK", true, Instant.now(), Instant.now()));

    mvc.perform(
            post("/api/v1/branches")
                .with(token(UUID.randomUUID(), branchId, "admin", "super-admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/branches/" + branchId))
        .andExpect(jsonPath("$.code").value("KDK"));
  }

  @Test
  void businessRuleViolationBecomesProblemDetail() throws Exception {
    when(createBranch.create(any())).thenThrow(new DomainException(ErrorCode.BRANCH_CODE_TAKEN));

    mvc.perform(
            post("/api/v1/branches")
                .with(token(UUID.randomUUID(), branchId, "admin", "super-admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("BRANCH_CODE_TAKEN"))
        .andExpect(jsonPath("$.detail").value("Bu şube kodu zaten kullanılıyor."));
  }

  @Test
  void englishClientsGetEnglishMessages() throws Exception {
    when(getBranch.get(any(), eq(branchId)))
        .thenThrow(new DomainException(ErrorCode.BRANCH_NOT_FOUND));

    mvc.perform(
            get("/api/v1/branches/{id}", branchId)
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .header("Accept-Language", "en"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Branch not found."));
  }

  @Test
  void theCallerIsResolvedFromTheToken() throws Exception {
    UUID callerId = UUID.randomUUID();
    when(getBranch.get(any(), eq(branchId)))
        .thenReturn(
            new BranchView(branchId, "Kadıköy Şubesi", "KDK", true, Instant.now(), Instant.now()));

    mvc.perform(get("/api/v1/branches/{id}", branchId).with(token(callerId, branchId, "admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(branchId.toString()));
  }
}
