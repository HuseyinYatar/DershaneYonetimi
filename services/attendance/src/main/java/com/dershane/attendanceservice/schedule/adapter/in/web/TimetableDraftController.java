package com.dershane.attendanceservice.schedule.adapter.in.web;

import com.dershane.attendanceservice.schedule.adapter.in.web.dto.CommitTimetableDraftRequest;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.CommitTimetableDraftResponse;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.DraftLessonResponse;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.GenerateTimetableDraftRequest;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.MoveDraftLessonRequest;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.TimetableDraftResponse;
import com.dershane.attendanceservice.schedule.application.port.in.CommitTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.DiscardTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GetTimetableDraftQuery;
import com.dershane.attendanceservice.schedule.application.port.in.MoveDraftLessonCommand;
import com.dershane.attendanceservice.schedule.application.port.in.MoveDraftLessonUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.RemoveDraftLessonUseCase;
import com.dershane.attendanceservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.attendanceservice.shared.adapter.in.web.CurrentActor;
import com.dershane.attendanceservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Timetable drafts")
@RestController
@RequestMapping("/api/v1/timetable-drafts")
@RequiredArgsConstructor
@PreAuthorize(SecurityRoles.ANY_ADMIN)
class TimetableDraftController {

  private final GenerateTimetableDraftUseCase generate;
  private final GetTimetableDraftQuery getDraft;
  private final MoveDraftLessonUseCase moveLesson;
  private final RemoveDraftLessonUseCase removeLesson;
  private final DiscardTimetableDraftUseCase discard;
  private final CommitTimetableDraftUseCase commit;
  private final TimetableDraftWebMapper mapper;

  @Operation(
      summary = "Distribute the hours classrooms still need; replaces the branch's draft",
      description =
          "Reads the branch's teacher assignments from the user service. 503 when it is down.")
  @PostMapping
  public ResponseEntity<TimetableDraftResponse> generate(
      @CurrentActor Actor actor,
      @RequestBody(required = false) GenerateTimetableDraftRequest request) {
    TimetableDraftResponse created =
        mapper.toResponse(
            generate.generate(
                actor,
                mapper.toCommand(
                    request == null ? new GenerateTimetableDraftRequest(null, null) : request)));
    return ResponseEntity.created(URI.create("/api/v1/timetable-drafts/current")).body(created);
  }

  @Operation(summary = "The branch's current draft (404 when there is none)")
  @GetMapping("/current")
  public TimetableDraftResponse current(
      @CurrentActor Actor actor, @RequestParam(required = false) UUID branchId) {
    return mapper.toResponse(getDraft.current(actor, branchId));
  }

  @Operation(summary = "Give a draft lesson a new day and hours")
  @PutMapping("/current/lessons/{lessonId}")
  public DraftLessonResponse move(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID branchId,
      @PathVariable UUID lessonId,
      @Valid @RequestBody MoveDraftLessonRequest request) {
    return mapper.toResponse(
        moveLesson.move(
            actor,
            new MoveDraftLessonCommand(
                branchId, lessonId, request.dayOfWeek(), request.startsAt(), request.endsAt())));
  }

  @Operation(summary = "Remove a lesson from the draft")
  @DeleteMapping("/current/lessons/{lessonId}")
  public ResponseEntity<Void> removeLesson(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID branchId,
      @PathVariable UUID lessonId) {
    removeLesson.remove(actor, branchId, lessonId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Throw the draft away")
  @DeleteMapping("/current")
  public ResponseEntity<Void> discard(
      @CurrentActor Actor actor, @RequestParam(required = false) UUID branchId) {
    discard.discard(actor, branchId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Save the draft as class schedules, all or nothing")
  @PostMapping("/current/commit")
  public CommitTimetableDraftResponse commit(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID branchId,
      @Valid @RequestBody CommitTimetableDraftRequest request) {
    return new CommitTimetableDraftResponse(commit.commit(actor, branchId, request.validFrom()));
  }
}
