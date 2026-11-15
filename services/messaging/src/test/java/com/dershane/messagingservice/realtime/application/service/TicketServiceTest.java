package com.dershane.messagingservice.realtime.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.messagingservice.directory.application.port.in.DirectoryEntryView;
import com.dershane.messagingservice.directory.application.port.in.DirectoryRole;
import com.dershane.messagingservice.directory.application.port.in.UserDirectoryQuery;
import com.dershane.messagingservice.realtime.application.port.out.TicketStorePort;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.application.MessagingProperties;
import com.dershane.messagingservice.shared.domain.DomainException;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

  @Mock private TicketStorePort tickets;
  @Mock private UserDirectoryQuery directory;
  @Mock private MessagingProperties properties;
  @Mock private MessagingProperties.Realtime realtime;

  private final UUID branchId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final Actor student = new Actor(userId, Set.of(Actor.STUDENT), branchId);

  private TicketService service;

  @BeforeEach
  void setUp() {
    service = new TicketService(tickets, directory, properties);
  }

  private Optional<DirectoryEntryView> entry(boolean active) {
    return Optional.of(
        new DirectoryEntryView(userId, DirectoryRole.STUDENT, branchId, "S", "S", active));
  }

  @Test
  void anActiveUserGetsATicketThatIsStoredWithItsLifetime() {
    when(directory.find(userId)).thenReturn(entry(true));
    when(properties.realtime()).thenReturn(realtime);
    when(realtime.ticketTtl()).thenReturn(Duration.ofSeconds(30));

    var ticket = service.issue(student);

    assertThat(ticket.expiresInSeconds()).isEqualTo(30);
    verify(tickets)
        .store(
            any(),
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30)));
  }

  @Test
  void aDeactivatedUserWithAStillValidTokenGetsNoTicket() {
    when(directory.find(userId)).thenReturn(entry(false));

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.issue(student))
        .extracting(DomainException::getErrorCode)
        .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
    verify(tickets, never()).store(any(), any(), any());
  }
}
