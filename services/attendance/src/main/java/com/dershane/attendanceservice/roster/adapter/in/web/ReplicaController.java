package com.dershane.attendanceservice.roster.adapter.in.web;

import com.dershane.attendanceservice.roster.adapter.in.web.dto.CloseMembershipRequest;
import com.dershane.attendanceservice.roster.adapter.in.web.dto.GuardianLinkRequest;
import com.dershane.attendanceservice.roster.adapter.in.web.dto.MembershipReplicaRequest;
import com.dershane.attendanceservice.roster.application.port.in.LinkGuardianUseCase;
import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipAddedUseCase;
import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipRemovedUseCase;
import com.dershane.attendanceservice.roster.application.port.in.UnlinkGuardianUseCase;
import com.dershane.attendanceservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.attendanceservice.shared.adapter.in.web.CurrentActor;
import com.dershane.attendanceservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary adapter: an admin feeds the copies of memberships and guardian links by hand. It goes
 * away when the SQS consumer for the user service's events calls the same use cases.
 */
@Tag(name = "Replicas (temporary)")
@RestController
@RequestMapping("/api/v1/attendance-replicas")
@RequiredArgsConstructor
class ReplicaController {

  private final RecordMembershipAddedUseCase recordAdded;
  private final RecordMembershipRemovedUseCase recordRemoved;
  private final LinkGuardianUseCase linkGuardian;
  private final UnlinkGuardianUseCase unlinkGuardian;
  private final RosterWebMapper mapper;

  @Operation(summary = "Record a student's classroom membership (repeating it changes nothing)")
  @PutMapping("/memberships/{membershipId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public void recordMembership(
      @CurrentActor Actor actor,
      @PathVariable UUID membershipId,
      @Valid @RequestBody MembershipReplicaRequest request) {
    recordAdded.recordAdded(actor, mapper.toCommand(membershipId, request));
  }

  @Operation(summary = "Close a membership on a date (inclusive)")
  @PostMapping("/memberships/{membershipId}/close")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public void closeMembership(
      @CurrentActor Actor actor,
      @PathVariable UUID membershipId,
      @Valid @RequestBody CloseMembershipRequest request) {
    recordRemoved.recordRemoved(actor, membershipId, request.endDate());
  }

  @Operation(summary = "Set the guardian of a student")
  @PutMapping("/guardian-links/{studentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public void linkGuardian(
      @CurrentActor Actor actor,
      @PathVariable UUID studentId,
      @Valid @RequestBody GuardianLinkRequest request) {
    linkGuardian.link(actor, request.guardianId(), studentId);
  }

  @Operation(summary = "Remove the guardian link of a student")
  @DeleteMapping("/guardian-links/{studentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public void unlinkGuardian(@CurrentActor Actor actor, @PathVariable UUID studentId) {
    unlinkGuardian.unlink(actor, studentId);
  }
}
