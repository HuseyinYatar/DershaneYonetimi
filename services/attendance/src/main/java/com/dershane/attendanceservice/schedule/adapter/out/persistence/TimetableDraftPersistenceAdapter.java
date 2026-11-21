package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableDraftPort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveTimetableDraftPort;
import com.dershane.attendanceservice.schedule.domain.TimetableDraft;
import com.dershane.attendanceservice.schedule.domain.TimetableDraft.DraftLesson;
import com.dershane.attendanceservice.schedule.domain.TimetableDraft.MissingHours;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The draft is small, so a save rewrites its lessons and missing hours instead of diffing them.
 * Hand-mapped: it is a parent with two child lists rather than a flat record.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class TimetableDraftPersistenceAdapter
    implements LoadTimetableDraftPort, SaveTimetableDraftPort {

  private final TimetableDraftJpaRepository drafts;
  private final TimetableDraftLessonJpaRepository lessons;
  private final TimetableDraftMissingJpaRepository missing;

  @Override
  public Optional<TimetableDraft> findByBranchId(UUID branchId) {
    return drafts
        .findByBranchId(branchId)
        .map(
            entity ->
                toDomain(
                    entity,
                    lessons.findByDraftIdOrderByDayOfWeekAscStartsAtAsc(entity.getId()),
                    missing.findByDraftId(entity.getId())));
  }

  @Override
  public TimetableDraft save(TimetableDraft draft) {
    TimetableDraftJpaEntity entity =
        drafts
            .findById(draft.getId())
            .orElseGet(
                () -> {
                  TimetableDraftJpaEntity created = new TimetableDraftJpaEntity();
                  created.setId(draft.getId());
                  created.setBranchId(draft.getBranchId());
                  return drafts.saveAndFlush(created);
                });
    lessons.deleteByDraftId(entity.getId());
    missing.deleteByDraftId(entity.getId());
    List<TimetableDraftLessonJpaEntity> savedLessons =
        lessons.saveAll(draft.getLessons().stream().map(l -> toEntity(entity.getId(), l)).toList());
    List<TimetableDraftMissingJpaEntity> savedMissing =
        missing.saveAll(draft.getMissing().stream().map(m -> toEntity(entity.getId(), m)).toList());
    lessons.flush();
    return toDomain(entity, savedLessons, savedMissing);
  }

  @Override
  public void deleteByBranchId(UUID branchId) {
    drafts
        .findByBranchId(branchId)
        .ifPresent(
            entity -> {
              lessons.deleteByDraftId(entity.getId());
              missing.deleteByDraftId(entity.getId());
              drafts.deleteDraft(entity.getId());
            });
  }

  private static TimetableDraftLessonJpaEntity toEntity(UUID draftId, DraftLesson l) {
    TimetableDraftLessonJpaEntity entity = new TimetableDraftLessonJpaEntity();
    entity.setId(l.id());
    entity.setDraftId(draftId);
    entity.setClassroomId(l.classroomId());
    entity.setTeacherId(l.teacherId());
    entity.setSubjectId(l.subjectId());
    entity.setDayOfWeek(l.dayOfWeek());
    entity.setStartsAt(l.startsAt());
    entity.setEndsAt(l.endsAt());
    return entity;
  }

  private static TimetableDraftMissingJpaEntity toEntity(UUID draftId, MissingHours m) {
    TimetableDraftMissingJpaEntity entity = new TimetableDraftMissingJpaEntity();
    entity.setId(UUID.randomUUID());
    entity.setDraftId(draftId);
    entity.setClassroomId(m.classroomId());
    entity.setTeacherId(m.teacherId());
    entity.setSubjectId(m.subjectId());
    entity.setMissingHours(m.hours());
    return entity;
  }

  private static TimetableDraft toDomain(
      TimetableDraftJpaEntity entity,
      List<TimetableDraftLessonJpaEntity> lessonEntities,
      List<TimetableDraftMissingJpaEntity> missingEntities) {
    return new TimetableDraft(
        entity.getId(),
        entity.getBranchId(),
        entity.getCreatedAt(),
        lessonEntities.stream()
            .map(
                l ->
                    new DraftLesson(
                        l.getId(),
                        l.getClassroomId(),
                        l.getTeacherId(),
                        l.getSubjectId(),
                        l.getDayOfWeek(),
                        l.getStartsAt(),
                        l.getEndsAt()))
            .toList(),
        missingEntities.stream()
            .map(
                m ->
                    new MissingHours(
                        m.getClassroomId(),
                        m.getTeacherId(),
                        m.getSubjectId(),
                        m.getMissingHours()))
            .toList());
  }
}
