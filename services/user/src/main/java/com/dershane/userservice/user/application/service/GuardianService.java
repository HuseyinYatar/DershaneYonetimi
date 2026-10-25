package com.dershane.userservice.user.application.service;

import com.dershane.userservice.branch.application.port.in.LoadBranchQuery;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.in.GetGuardianQuery;
import com.dershane.userservice.user.application.port.in.GuardianView;
import com.dershane.userservice.user.application.port.in.ListGuardiansQuery;
import com.dershane.userservice.user.application.port.in.RegisterGuardianCommand;
import com.dershane.userservice.user.application.port.in.RegisterGuardianUseCase;
import com.dershane.userservice.user.application.port.in.RegistrationResult;
import com.dershane.userservice.user.application.port.in.UpdateGuardianCommand;
import com.dershane.userservice.user.application.port.in.UpdateGuardianUseCase;
import com.dershane.userservice.user.application.port.out.GuardianQueryPort;
import com.dershane.userservice.user.domain.Guardian;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Role;
import com.dershane.userservice.user.domain.UsernamePolicy;
import io.micrometer.observation.annotation.Observed;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class GuardianService
    implements RegisterGuardianUseCase,
        UpdateGuardianUseCase,
        GetGuardianQuery,
        ListGuardiansQuery {

  private static final String CREATE = "guardian.create";
  private static final String UPDATE = "guardian.update";

  private final GuardianQueryPort guardianQuery;
  private final UserLookup userLookup;
  private final LoadBranchQuery branchLookup;
  private final UserAccountService accountService;
  private final UsernamePolicy usernamePolicy;
  private final BranchAccessPolicy branchAccess;
  private final UserAccessPolicy userAccess;

  @Override
  @Observed(name = CREATE)
  public RegistrationResult<GuardianView> register(Actor actor, RegisterGuardianCommand command) {
    branchAccess.requireBranchAdmin(actor, command.branchId(), CREATE);
    branchLookup.requireActive(command.branchId(), CREATE);
    PersonalInfo info =
        new PersonalInfo(command.firstName(), command.lastName(), command.email(), command.phone());
    String username = usernamePolicy.forGuardian(info);
    AccountCreated<Guardian> created =
        accountService.create(
            new NewAccount(Role.GUARDIAN, command.branchId(), info, username),
            id -> Guardian.register(id, command.branchId(), info, username));
    return new RegistrationResult<>(
        requireView(created.user().getId()),
        created.passwordSetupEmailSent(),
        created.temporaryPassword());
  }

  @Override
  @Observed(name = UPDATE)
  public GuardianView update(Actor actor, UUID guardianId, UpdateGuardianCommand command) {
    Guardian guardian = userLookup.requireGuardian(guardianId);
    userAccess.requireCanManage(actor, guardian, UPDATE);
    guardian.requireActive(UPDATE);
    PersonalInfo newInfo =
        guardian
            .personalInfo()
            .merge(
                new PersonalInfo(
                    command.firstName(), command.lastName(), command.email(), command.phone()));
    Guardian saved = accountService.update(guardian, newInfo, g -> {});
    return requireView(saved.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public GuardianView get(Actor actor, UUID guardianId) {
    Guardian guardian = userLookup.requireGuardian(guardianId);
    userAccess.requireCanView(actor, guardian);
    return requireView(guardianId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<GuardianView> list(Actor actor, UUID branchId, Boolean active, Pageable pageable) {
    UUID branchFilter = branchAccess.resolveBranchFilter(actor, branchId, "guardian.list");
    return guardianQuery.findGuardianViews(branchFilter, active, pageable);
  }

  private GuardianView requireView(UUID guardianId) {
    return guardianQuery
        .findGuardianViewById(guardianId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.GUARDIAN_NOT_FOUND,
                    "Guardian not found",
                    "guardianId",
                    guardianId));
  }
}
