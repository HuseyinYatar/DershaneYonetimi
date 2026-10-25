package com.dershane.userservice.user.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.user.adapter.in.web.dto.TemporaryPasswordResponse;
import com.dershane.userservice.user.adapter.in.web.dto.UserResponse;
import com.dershane.userservice.user.application.port.in.DeactivateUserUseCase;
import com.dershane.userservice.user.application.port.in.GetUserQuery;
import com.dershane.userservice.user.application.port.in.ResetTemporaryPasswordUseCase;
import com.dershane.userservice.user.application.port.in.SendPasswordSetupEmailUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users/{userId}")
@RequiredArgsConstructor
class UserController {

  private final GetUserQuery getUser;
  private final DeactivateUserUseCase deactivateUser;
  private final SendPasswordSetupEmailUseCase sendPasswordSetupEmail;
  private final ResetTemporaryPasswordUseCase resetTemporaryPassword;
  private final UserWebMapper mapper;

  @Operation(summary = "Get any user's common profile")
  @GetMapping
  public UserResponse get(@CurrentActor Actor actor, @PathVariable UUID userId) {
    return mapper.toResponse(getUser.get(actor, userId));
  }

  @Operation(
      summary = "Deactivate a user",
      description =
          "Disables the login, marks the profile inactive and, for students, closes the active"
              + " classroom membership. Records are kept.")
  @PostMapping("/deactivate")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public UserResponse deactivate(@CurrentActor Actor actor, @PathVariable UUID userId) {
    return mapper.toResponse(deactivateUser.deactivate(actor, userId));
  }

  @Operation(summary = "Resend the password setup e-mail (users with e-mail)")
  @PostMapping("/password-setup-email")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<Void> sendPasswordSetupEmail(
      @CurrentActor Actor actor, @PathVariable UUID userId) {
    sendPasswordSetupEmail.send(actor, userId);
    return ResponseEntity.accepted().build();
  }

  @Operation(summary = "Issue a new temporary password (users without e-mail)")
  @PostMapping("/temporary-password")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public TemporaryPasswordResponse resetTemporaryPassword(
      @CurrentActor Actor actor, @PathVariable UUID userId) {
    return mapper.toResponse(resetTemporaryPassword.reset(actor, userId));
  }
}
