package com.dershane.userservice.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dershane.userservice.shared.application.MetricNames;
import com.dershane.userservice.shared.application.event.IntegrationEvent.UserUpdated;
import com.dershane.userservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.support.TestTransactions;
import com.dershane.userservice.user.application.event.UserDeactivated;
import com.dershane.userservice.user.application.port.out.IdentityProfile;
import com.dershane.userservice.user.application.port.out.IdentityProviderPort;
import com.dershane.userservice.user.application.port.out.LoadUserPort;
import com.dershane.userservice.user.application.port.out.NewIdentity;
import com.dershane.userservice.user.application.port.out.SaveUserPort;
import com.dershane.userservice.user.domain.Guardian;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Role;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.TargetExam;
import com.dershane.userservice.user.domain.TemporaryPasswordGenerator;
import com.dershane.userservice.user.domain.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

  @Mock private IdentityProviderPort identityProvider;
  @Mock private LoadUserPort loadUser;
  @Mock private SaveUserPort saveUser;
  @Mock private IntegrationEventPublisher events;
  @Mock private ApplicationEventPublisher applicationEvents;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final UUID branchId = UUID.randomUUID();
  private final UUID accountId = UUID.randomUUID();
  private UserAccountService service;

  @BeforeEach
  void setUp() {
    service =
        new UserAccountService(
            identityProvider,
            loadUser,
            saveUser,
            events,
            new UserEventFactory(),
            applicationEvents,
            TestTransactions.template(),
            new TemporaryPasswordGenerator(),
            meterRegistry);
  }

  @Test
  void accountWithoutEmailGetsATemporaryPasswordAndAUserUpdatedEvent() {
    PersonalInfo info = new PersonalInfo("Ali", "Kaya", null, null);
    when(identityProvider.createAccount(any())).thenReturn(accountId);
    when(saveUser.save(any(Student.class))).thenAnswer(inv -> versioned(inv.getArgument(0)));

    AccountCreated<Student> created =
        service.create(
            new NewAccount(Role.STUDENT, branchId, info, "mrkz-1042"), id -> student(id, info));

    ArgumentCaptor<NewIdentity> identity = ArgumentCaptor.forClass(NewIdentity.class);
    verify(identityProvider).createAccount(identity.capture());
    assertThat(identity.getValue().temporaryPassword()).hasSize(12);
    assertThat(created.temporaryPassword()).isEqualTo(identity.getValue().temporaryPassword());
    assertThat(created.passwordSetupEmailSent()).isFalse();
    assertThat(created.user().getId()).isEqualTo(accountId);
    verify(events).publish(any(UserUpdated.class));
    verify(identityProvider, never()).sendPasswordSetupEmail(any());
    assertThat(meterRegistry.counter(MetricNames.USER_REGISTRATIONS, "role", "STUDENT").count())
        .isEqualTo(1.0);
  }

  @Test
  void accountWithEmailGetsThePasswordSetupEmail() {
    PersonalInfo info = new PersonalInfo("Ali", "Kaya", "Ali@Example.com", null);
    when(identityProvider.createAccount(any())).thenReturn(accountId);
    when(saveUser.save(any(Student.class))).thenAnswer(inv -> versioned(inv.getArgument(0)));

    AccountCreated<Student> created =
        service.create(
            new NewAccount(Role.STUDENT, branchId, info, info.email()), id -> student(id, info));

    assertThat(created.temporaryPassword()).isNull();
    assertThat(created.passwordSetupEmailSent()).isTrue();
    verify(identityProvider).sendPasswordSetupEmail(accountId);
  }

  @Test
  void failedProfileSaveRemovesTheKeycloakAccount() {
    PersonalInfo info = new PersonalInfo("Ali", "Kaya", null, null);
    when(identityProvider.createAccount(any())).thenReturn(accountId);
    when(saveUser.save(any(Student.class)))
        .thenThrow(new DataIntegrityViolationException("uk_student_branch_number"));

    assertThatThrownBy(
            () ->
                service.create(
                    new NewAccount(Role.STUDENT, branchId, info, "mrkz-1042"),
                    id -> student(id, info)))
        .isInstanceOf(DataIntegrityViolationException.class);

    verify(identityProvider).deleteAccount(accountId);
    verifyNoInteractions(events);
    assertThat(compensations("deleteAccount", "success")).isEqualTo(1.0);
  }

  @Test
  void failedCompensationIsCountedSoTheOrphanCanBeFound() {
    PersonalInfo info = new PersonalInfo("Ali", "Kaya", null, null);
    when(identityProvider.createAccount(any())).thenReturn(accountId);
    when(saveUser.save(any(Student.class))).thenThrow(new DataIntegrityViolationException("boom"));
    doThrow(new DomainException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE))
        .when(identityProvider)
        .deleteAccount(accountId);

    assertThatThrownBy(
            () ->
                service.create(
                    new NewAccount(Role.STUDENT, branchId, info, "mrkz-1042"),
                    id -> student(id, info)))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(compensations("deleteAccount", "failure")).isEqualTo(1.0);
  }

  @Test
  void takenEmailIsRejectedBeforeKeycloakIsCalled() {
    PersonalInfo info = new PersonalInfo("Ali", "Kaya", "ali@example.com", null);
    when(loadUser.existsByEmail("ali@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    new NewAccount(Role.STUDENT, branchId, info, info.email()),
                    id -> student(id, info)))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMAIL_TAKEN);
    verifyNoInteractions(identityProvider);
  }

  @Test
  void failedProfileUpdateRestoresTheKeycloakProfile() {
    Guardian guardian =
        Guardian.register(
            accountId, branchId, new PersonalInfo("Ayşe", "Kaya", null, "05551112233"), "x");
    PersonalInfo renamed = new PersonalInfo("Ayşe", "Yılmaz", null, "05551112233");
    when(loadUser.findById(accountId)).thenReturn(Optional.of(guardian));
    when(saveUser.save(any(Guardian.class))).thenThrow(new DataIntegrityViolationException("boom"));

    assertThatThrownBy(() -> service.update(guardian, renamed, g -> {}))
        .isInstanceOf(DataIntegrityViolationException.class);

    ArgumentCaptor<IdentityProfile> profiles = ArgumentCaptor.forClass(IdentityProfile.class);
    verify(identityProvider, times(2)).updateProfile(eq(accountId), profiles.capture());
    assertThat(profiles.getAllValues().get(0).lastName()).isEqualTo("Yılmaz");
    assertThat(profiles.getAllValues().get(1).lastName()).isEqualTo("Kaya");
  }

  @Test
  void deactivationDisablesLoginFirstThenLetsOtherModulesCloseTheStudentsClassroom() {
    Student student = student(accountId, new PersonalInfo("Ali", "Kaya", null, null));
    when(loadUser.findById(accountId)).thenReturn(Optional.of(student));
    when(saveUser.save(any(Student.class))).thenAnswer(inv -> versioned(inv.getArgument(0)));

    User saved = service.deactivate(student);

    assertThat(saved.isActive()).isFalse();
    InOrder order = inOrder(identityProvider, applicationEvents, saveUser, events);
    order.verify(identityProvider).setEnabled(accountId, false);
    order.verify(applicationEvents).publishEvent(new UserDeactivated(accountId, Role.STUDENT));
    order.verify(saveUser).save(any(Student.class));
    order.verify(events).publish(any(UserUpdated.class));
  }

  @Test
  void failedDeactivationReEnablesTheLogin() {
    Student student = student(accountId, new PersonalInfo("Ali", "Kaya", null, null));
    when(loadUser.findById(accountId)).thenReturn(Optional.of(student));
    when(saveUser.save(any(Student.class))).thenThrow(new DataIntegrityViolationException("boom"));

    assertThatThrownBy(() -> service.deactivate(student))
        .isInstanceOf(DataIntegrityViolationException.class);

    verify(identityProvider).setEnabled(accountId, true);
    assertThat(compensations("reEnable", "success")).isEqualTo(1.0);
  }

  private Student student(UUID id, PersonalInfo info) {
    return Student.register(id, branchId, info, "mrkz-1042", "1042", 12, TargetExam.YKS);
  }

  /** Simulates the version Hibernate assigns on flush. */
  private static <T extends User> T versioned(T user) {
    ReflectionTestUtils.setField(user, "version", 0L);
    return user;
  }

  private double compensations(String action, String outcome) {
    return meterRegistry
        .counter(MetricNames.IDENTITY_COMPENSATIONS, "action", action, "outcome", outcome)
        .count();
  }
}
