package com.dershane.userservice.user.application.service;

import com.dershane.userservice.branch.application.port.in.LoadBranchQuery;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.in.ListAdminsQuery;
import com.dershane.userservice.user.application.port.in.RegisterAdminCommand;
import com.dershane.userservice.user.application.port.in.RegisterAdminUseCase;
import com.dershane.userservice.user.application.port.in.RegistrationResult;
import com.dershane.userservice.user.application.port.in.UpdateAdminCommand;
import com.dershane.userservice.user.application.port.in.UpdateAdminUseCase;
import com.dershane.userservice.user.application.port.in.UserView;
import com.dershane.userservice.user.application.port.out.UserQueryPort;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Role;
import com.dershane.userservice.user.domain.User;
import com.dershane.userservice.user.domain.UsernamePolicy;
import io.micrometer.observation.annotation.Observed;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branch admins. Every entry point is super-admin only (checked at the controller). The extra
 * {@code super-admin} realm role is granted in Keycloak, not through this API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class AdminService implements RegisterAdminUseCase, UpdateAdminUseCase, ListAdminsQuery {

  private static final String CREATE = "admin.create";

  private final UserQueryPort userQuery;
  private final UserLookup userLookup;
  private final LoadBranchQuery branchLookup;
  private final UserAccountService accountService;
  private final UsernamePolicy usernamePolicy;

  @Override
  @Observed(name = CREATE)
  public RegistrationResult<UserView> register(RegisterAdminCommand command) {
    branchLookup.requireActive(command.branchId(), CREATE);
    PersonalInfo info =
        new PersonalInfo(command.firstName(), command.lastName(), command.email(), command.phone());
    String username = usernamePolicy.forStaff(info, Role.ADMIN.name());
    AccountCreated<User> created =
        accountService.create(
            new NewAccount(Role.ADMIN, command.branchId(), info, username),
            id -> User.admin(id, command.branchId(), info, username));
    return new RegistrationResult<>(
        requireView(created.user().getId()),
        created.passwordSetupEmailSent(),
        created.temporaryPassword());
  }

  @Override
  @Observed(name = "admin.update")
  public UserView update(UUID adminId, UpdateAdminCommand command) {
    User admin = userLookup.requireAdmin(adminId);
    admin.requireActive("admin.update");
    PersonalInfo newInfo =
        admin
            .personalInfo()
            .merge(
                new PersonalInfo(
                    command.firstName(), command.lastName(), command.email(), command.phone()));
    User saved = accountService.update(admin, newInfo, a -> {});
    return requireView(saved.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public Page<UserView> list(UUID branchId, Pageable pageable) {
    return userQuery.findAdminViews(branchId, pageable);
  }

  private UserView requireView(UUID adminId) {
    return userQuery
        .findViewById(adminId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.ADMIN_NOT_FOUND, "Admin not found", "userId", adminId));
  }
}
