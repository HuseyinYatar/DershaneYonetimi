package com.dershane.userservice.user.application.service;

import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.in.DeactivateUserUseCase;
import com.dershane.userservice.user.application.port.in.GetMyProfileQuery;
import com.dershane.userservice.user.application.port.in.GetUserQuery;
import com.dershane.userservice.user.application.port.in.GuardianChildView;
import com.dershane.userservice.user.application.port.in.ListMyChildrenQuery;
import com.dershane.userservice.user.application.port.in.ResetTemporaryPasswordUseCase;
import com.dershane.userservice.user.application.port.in.SendPasswordSetupEmailUseCase;
import com.dershane.userservice.user.application.port.in.TemporaryPasswordResult;
import com.dershane.userservice.user.application.port.in.UserView;
import com.dershane.userservice.user.application.port.out.StudentGuardianQueryPort;
import com.dershane.userservice.user.application.port.out.UserQueryPort;
import com.dershane.userservice.user.domain.User;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operations common to all roles, and the caller's own data ({@code /me}). */
@Slf4j
@Service
@RequiredArgsConstructor
class UserService
    implements GetUserQuery,
        GetMyProfileQuery,
        DeactivateUserUseCase,
        SendPasswordSetupEmailUseCase,
        ResetTemporaryPasswordUseCase,
        ListMyChildrenQuery {

  private static final String DEACTIVATE = "user.deactivate";

  private final UserQueryPort userQuery;
  private final StudentGuardianQueryPort studentGuardianQuery;
  private final UserLookup userLookup;
  private final UserAccountService accountService;
  private final UserAccessPolicy userAccess;

  @Override
  @Transactional(readOnly = true)
  public UserView get(Actor actor, UUID userId) {
    User user = userLookup.require(userId);
    userAccess.requireCanView(actor, user);
    return requireView(userId);
  }

  @Override
  @Observed(name = DEACTIVATE)
  public UserView deactivate(Actor actor, UUID userId) {
    User user = userLookup.require(userId);
    userAccess.requireCanManage(actor, user, DEACTIVATE);
    if (actor.id().equals(userId)) {
      throw Rejections.reject(
          log,
          ErrorCode.CANNOT_DEACTIVATE_SELF,
          "Users can't deactivate themselves",
          "userId",
          userId);
    }
    if (!user.isActive()) {
      log.atInfo().addKeyValue("userId", userId).log("User already inactive; no change");
      return requireView(userId);
    }
    accountService.deactivate(user);
    return requireView(userId);
  }

  @Override
  @Observed(name = "user.password-setup-email")
  public void send(Actor actor, UUID userId) {
    User user = userLookup.require(userId);
    userAccess.requireCanManage(actor, user, "user.password-email");
    user.requireActive("user.password-email");
    if (user.getInfo().email() == null) {
      throw Rejections.reject(
          log,
          ErrorCode.EMAIL_NOT_AVAILABLE,
          "Password setup e-mail rejected: user has no e-mail; use a temporary password",
          "userId",
          userId);
    }
    accountService.sendPasswordSetupEmail(user);
  }

  @Override
  @Observed(name = "user.temporary-password")
  public TemporaryPasswordResult reset(Actor actor, UUID userId) {
    User user = userLookup.require(userId);
    userAccess.requireCanManage(actor, user, "user.temporary-password");
    user.requireActive("user.temporary-password");
    if (user.getInfo().email() != null) {
      throw Rejections.reject(
          log,
          ErrorCode.TEMPORARY_PASSWORD_NOT_ALLOWED,
          "Temporary password rejected: user has an e-mail address",
          "userId",
          userId);
    }
    return new TemporaryPasswordResult(accountService.resetTemporaryPassword(user));
  }

  @Override
  @Transactional(readOnly = true)
  public UserView me(Actor actor) {
    return requireView(actor.id());
  }

  @Override
  @Transactional(readOnly = true)
  public List<GuardianChildView> myChildren(Actor actor) {
    if (!actor.isGuardian()) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Endpoint not available for the caller's role",
          "callerId",
          actor.id(),
          "action",
          "me.children");
    }
    return studentGuardianQuery.findChildren(actor.id());
  }

  private UserView requireView(UUID userId) {
    return userQuery
        .findViewById(userId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.USER_NOT_FOUND, "User not found", "userId", userId));
  }
}
