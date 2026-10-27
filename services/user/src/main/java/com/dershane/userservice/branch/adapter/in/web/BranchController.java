package com.dershane.userservice.branch.adapter.in.web;

import com.dershane.userservice.branch.adapter.in.web.dto.BranchResponse;
import com.dershane.userservice.branch.adapter.in.web.dto.CreateBranchRequest;
import com.dershane.userservice.branch.adapter.in.web.dto.UpdateBranchRequest;
import com.dershane.userservice.branch.application.port.in.CreateBranchUseCase;
import com.dershane.userservice.branch.application.port.in.DeactivateBranchUseCase;
import com.dershane.userservice.branch.application.port.in.GetBranchQuery;
import com.dershane.userservice.branch.application.port.in.ListBranchesQuery;
import com.dershane.userservice.branch.application.port.in.UpdateBranchUseCase;
import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.userservice.shared.application.Actor;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Branches")
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
class BranchController {

  private final CreateBranchUseCase createBranch;
  private final UpdateBranchUseCase updateBranch;
  private final DeactivateBranchUseCase deactivateBranch;
  private final GetBranchQuery getBranch;
  private final ListBranchesQuery listBranches;
  private final BranchWebMapper mapper;

  @Operation(summary = "Create a branch (super admin)")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.ONLY_SUPER_ADMIN)
  public ResponseEntity<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
    BranchResponse created = mapper.toResponse(createBranch.create(mapper.toCommand(request)));
    return ResponseEntity.created(URI.create("/api/v1/branches/" + created.id())).body(created);
  }

  @Operation(summary = "List branches (super admin: all, admin: own branch)")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public PagedModel<BranchResponse> list(@CurrentActor Actor actor, Pageable pageable) {
    return new PagedModel<>(listBranches.list(actor, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get a branch")
  @GetMapping("/{branchId}")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public BranchResponse get(@CurrentActor Actor actor, @PathVariable UUID branchId) {
    return mapper.toResponse(getBranch.get(actor, branchId));
  }

  @Operation(summary = "Rename a branch (super admin)")
  @PatchMapping("/{branchId}")
  @PreAuthorize(SecurityRoles.ONLY_SUPER_ADMIN)
  public BranchResponse update(
      @PathVariable UUID branchId, @Valid @RequestBody UpdateBranchRequest request) {
    return mapper.toResponse(updateBranch.update(branchId, mapper.toCommand(request)));
  }

  @Operation(summary = "Deactivate a branch (super admin); fails while it has active classrooms")
  @PostMapping("/{branchId}/deactivate")
  @PreAuthorize(SecurityRoles.ONLY_SUPER_ADMIN)
  public BranchResponse deactivate(@PathVariable UUID branchId) {
    return mapper.toResponse(deactivateBranch.deactivate(branchId));
  }
}
