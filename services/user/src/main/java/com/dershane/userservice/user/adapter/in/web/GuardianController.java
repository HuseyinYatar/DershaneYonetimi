package com.dershane.userservice.user.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.user.adapter.in.web.dto.CreateGuardianRequest;
import com.dershane.userservice.user.adapter.in.web.dto.GuardianChildResponse;
import com.dershane.userservice.user.adapter.in.web.dto.GuardianResponse;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateGuardianRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UserCreatedResponse;
import com.dershane.userservice.user.application.port.in.GetGuardianQuery;
import com.dershane.userservice.user.application.port.in.ListGuardianChildrenQuery;
import com.dershane.userservice.user.application.port.in.ListGuardiansQuery;
import com.dershane.userservice.user.application.port.in.RegisterGuardianUseCase;
import com.dershane.userservice.user.application.port.in.UpdateGuardianUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Guardians")
@RestController
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
class GuardianController {

  private final RegisterGuardianUseCase registerGuardian;
  private final UpdateGuardianUseCase updateGuardian;
  private final GetGuardianQuery getGuardian;
  private final ListGuardiansQuery listGuardians;
  private final ListGuardianChildrenQuery listChildren;
  private final UserWebMapper mapper;

  @Operation(
      summary = "Register a guardian (phone required) and open their login account",
      description =
          "Without e-mail, the username is the phone number and a temporary password"
              + " is returned once.")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<UserCreatedResponse<GuardianResponse>> create(
      @CurrentActor Actor actor, @Valid @RequestBody CreateGuardianRequest request) {
    UserCreatedResponse<GuardianResponse> created =
        UserCreatedResponse.of(
            registerGuardian.register(actor, mapper.toCommand(request)), mapper::toResponse);
    return ResponseEntity.created(URI.create("/api/v1/guardians/" + created.user().id()))
        .body(created);
  }

  @Operation(summary = "List guardians (admins)")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public PagedModel<GuardianResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) Boolean active,
      Pageable pageable) {
    return new PagedModel<>(
        listGuardians.list(actor, branchId, active, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get a guardian")
  @GetMapping("/{guardianId}")
  public GuardianResponse get(@CurrentActor Actor actor, @PathVariable UUID guardianId) {
    return mapper.toResponse(getGuardian.get(actor, guardianId));
  }

  @Operation(summary = "Update a guardian; null fields are left unchanged")
  @PatchMapping("/{guardianId}")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public GuardianResponse update(
      @CurrentActor Actor actor,
      @PathVariable UUID guardianId,
      @Valid @RequestBody UpdateGuardianRequest request) {
    return mapper.toResponse(updateGuardian.update(actor, guardianId, mapper.toCommand(request)));
  }

  @Operation(summary = "Children of a guardian (admins or the guardian)")
  @GetMapping("/{guardianId}/children")
  public List<GuardianChildResponse> children(
      @CurrentActor Actor actor, @PathVariable UUID guardianId) {
    return listChildren.childrenOf(actor, guardianId).stream().map(mapper::toResponse).toList();
  }
}
