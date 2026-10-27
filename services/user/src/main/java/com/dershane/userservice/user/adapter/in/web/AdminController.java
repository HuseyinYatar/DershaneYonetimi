package com.dershane.userservice.user.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.userservice.user.adapter.in.web.dto.CreateAdminRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateAdminRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UserCreatedResponse;
import com.dershane.userservice.user.adapter.in.web.dto.UserResponse;
import com.dershane.userservice.user.application.port.in.ListAdminsQuery;
import com.dershane.userservice.user.application.port.in.RegisterAdminUseCase;
import com.dershane.userservice.user.application.port.in.UpdateAdminUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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

@Tag(name = "Admins")
@RestController
@RequestMapping("/api/v1/admins")
@RequiredArgsConstructor
@PreAuthorize(SecurityRoles.ONLY_SUPER_ADMIN)
class AdminController {

  private final RegisterAdminUseCase registerAdmin;
  private final UpdateAdminUseCase updateAdmin;
  private final ListAdminsQuery listAdmins;
  private final UserWebMapper mapper;

  @Operation(summary = "Create a branch admin (super admin)")
  @PostMapping
  @Idempotent
  public ResponseEntity<UserCreatedResponse<UserResponse>> create(
      @Valid @RequestBody CreateAdminRequest request) {
    UserCreatedResponse<UserResponse> created =
        UserCreatedResponse.of(
            registerAdmin.register(mapper.toCommand(request)), mapper::toResponse);
    return ResponseEntity.created(URI.create("/api/v1/users/" + created.user().id())).body(created);
  }

  @Operation(summary = "List admins (super admin)")
  @GetMapping
  public PagedModel<UserResponse> list(
      @RequestParam(required = false) UUID branchId, Pageable pageable) {
    return new PagedModel<>(listAdmins.list(branchId, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Update an admin (super admin)")
  @PatchMapping("/{adminId}")
  public UserResponse update(
      @PathVariable UUID adminId, @Valid @RequestBody UpdateAdminRequest request) {
    return mapper.toResponse(updateAdmin.update(adminId, mapper.toCommand(request)));
  }
}
