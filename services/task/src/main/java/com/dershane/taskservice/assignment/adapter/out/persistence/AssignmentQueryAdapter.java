package com.dershane.taskservice.assignment.adapter.out.persistence;

import com.dershane.taskservice.assignment.application.port.in.AssignmentDetailView;
import com.dershane.taskservice.assignment.application.port.in.AssignmentSummaryView;
import com.dershane.taskservice.assignment.application.port.in.EvaluationView;
import com.dershane.taskservice.assignment.application.port.in.SubmissionView;
import com.dershane.taskservice.assignment.application.port.out.AssignmentFilter;
import com.dershane.taskservice.assignment.application.port.out.AssignmentQueryPort;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Read side: assignments joined with their task, submission and evaluation in plain SQL. The
 * filters depend on the caller, so the statement is built from the filter; every value is bound as
 * a parameter.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AssignmentQueryAdapter implements AssignmentQueryPort {

  private static final String SUMMARY_COLUMNS =
      "a.id, a.task_id, t.title, a.student_id, a.occurrence_date, a.due_at, a.status";

  private static final String DETAIL_COLUMNS =
      "a.id, a.task_id, t.title, t.description, t.attachment_file_ids, t.teacher_id, t.branch_id,"
          + " a.student_id, a.occurrence_date, a.due_at, a.status, a.assigned_at,"
          + " s.description as submission_description, s.file_ids as submission_file_ids,"
          + " s.submitted_at, s.late,"
          + " e.evaluator_id, e.score, e.feedback, e.evaluated_at";

  private static final String SUMMARY_FROM = " from assignment a join task t on t.id = a.task_id";

  private static final String DETAIL_FROM =
      SUMMARY_FROM
          + " left join submission s on s.assignment_id = a.id"
          + " left join evaluation e on e.submission_id = s.id";

  private final JdbcClient jdbc;

  @Override
  public Optional<AssignmentDetailView> findDetailById(UUID assignmentId) {
    return jdbc.sql("select " + DETAIL_COLUMNS + DETAIL_FROM + " where a.id = :id")
        .param("id", assignmentId)
        .query(AssignmentQueryAdapter::toDetail)
        .optional();
  }

  @Override
  public Page<AssignmentSummaryView> findSummaries(AssignmentFilter filter, Pageable pageable) {
    Where where = where(filter);
    long total =
        jdbc.sql("select count(*)" + SUMMARY_FROM + where.sql())
            .params(where.params())
            .query(Long.class)
            .single();
    List<AssignmentSummaryView> rows =
        jdbc.sql(
                "select "
                    + SUMMARY_COLUMNS
                    + SUMMARY_FROM
                    + where.sql()
                    + " order by a.due_at desc, a.id limit :limit offset :offset")
            .params(where.params())
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(AssignmentQueryAdapter::toSummary)
            .list();
    return new PageImpl<>(rows, pageable, total);
  }

  private record Where(String sql, Map<String, Object> params) {}

  private static Where where(AssignmentFilter filter) {
    List<String> conditions = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    if (filter.studentId() != null) {
      conditions.add("a.student_id = :studentId");
      params.put("studentId", filter.studentId());
    }
    if (filter.teacherId() != null) {
      conditions.add("t.teacher_id = :teacherId");
      params.put("teacherId", filter.teacherId());
    }
    if (filter.branchId() != null) {
      conditions.add("t.branch_id = :branchId");
      params.put("branchId", filter.branchId());
    }
    if (filter.taskId() != null) {
      conditions.add("a.task_id = :taskId");
      params.put("taskId", filter.taskId());
    }
    if (filter.status() != null) {
      conditions.add("a.status = :status");
      params.put("status", filter.status().name());
    }
    if (filter.from() != null) {
      conditions.add("a.occurrence_date >= :from");
      params.put("from", filter.from());
    }
    if (filter.to() != null) {
      conditions.add("a.occurrence_date <= :to");
      params.put("to", filter.to());
    }
    String sql = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    return new Where(sql, params);
  }

  private static AssignmentSummaryView toSummary(ResultSet rs, int row) throws SQLException {
    return new AssignmentSummaryView(
        rs.getObject("id", UUID.class),
        rs.getObject("task_id", UUID.class),
        rs.getString("title"),
        rs.getObject("student_id", UUID.class),
        rs.getObject("occurrence_date", LocalDate.class),
        rs.getObject("due_at", OffsetDateTime.class).toInstant(),
        AssignmentStatus.valueOf(rs.getString("status")));
  }

  private static AssignmentDetailView toDetail(ResultSet rs, int row) throws SQLException {
    OffsetDateTime submittedAt = rs.getObject("submitted_at", OffsetDateTime.class);
    SubmissionView submission =
        submittedAt == null
            ? null
            : new SubmissionView(
                rs.getString("submission_description"),
                uuids(rs.getArray("submission_file_ids")),
                submittedAt.toInstant(),
                rs.getBoolean("late"));
    OffsetDateTime evaluatedAt = rs.getObject("evaluated_at", OffsetDateTime.class);
    EvaluationView evaluation =
        evaluatedAt == null
            ? null
            : new EvaluationView(
                rs.getObject("evaluator_id", UUID.class),
                rs.getObject("score", Integer.class),
                rs.getString("feedback"),
                evaluatedAt.toInstant());
    return new AssignmentDetailView(
        rs.getObject("id", UUID.class),
        rs.getObject("task_id", UUID.class),
        rs.getString("title"),
        rs.getString("description"),
        uuids(rs.getArray("attachment_file_ids")),
        rs.getObject("teacher_id", UUID.class),
        rs.getObject("branch_id", UUID.class),
        rs.getObject("student_id", UUID.class),
        rs.getObject("occurrence_date", LocalDate.class),
        rs.getObject("due_at", OffsetDateTime.class).toInstant(),
        AssignmentStatus.valueOf(rs.getString("status")),
        rs.getObject("assigned_at", OffsetDateTime.class).toInstant(),
        submission,
        evaluation);
  }

  private static List<UUID> uuids(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    return List.of((UUID[]) array.getArray());
  }
}
