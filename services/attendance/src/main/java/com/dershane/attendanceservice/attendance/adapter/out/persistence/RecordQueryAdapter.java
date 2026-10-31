package com.dershane.attendanceservice.attendance.adapter.out.persistence;

import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.application.port.out.RecordFilter;
import com.dershane.attendanceservice.attendance.application.port.out.RecordQueryPort;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
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
 * Read side: records joined with their session in plain SQL. The filters depend on the caller, so
 * the statement is built from the filter; every value is bound as a parameter.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class RecordQueryAdapter implements RecordQueryPort {

  private static final String COLUMNS =
      "r.id, r.session_id, r.student_id, s.classroom_id, s.subject_id, s.session_date,"
          + " s.starts_at, s.ends_at, r.status, r.excuse_id, r.note, r.marked_at, r.marked_by";

  private static final String FROM =
      " from attendance_record r join class_session s on s.id = r.session_id";

  private final JdbcClient jdbc;

  @Override
  public Page<AttendanceRecordView> findViews(RecordFilter filter, Pageable pageable) {
    Where where = where(filter);
    long total =
        jdbc.sql("select count(*)" + FROM + where.sql())
            .params(where.params())
            .query(Long.class)
            .single();
    List<AttendanceRecordView> rows =
        jdbc.sql(
                "select "
                    + COLUMNS
                    + FROM
                    + where.sql()
                    + " order by s.session_date desc, s.starts_at desc, r.id"
                    + " limit :limit offset :offset")
            .params(where.params())
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(RecordQueryAdapter::toView)
            .list();
    return new PageImpl<>(rows, pageable, total);
  }

  @Override
  public Optional<AttendanceRecordView> findViewById(UUID recordId) {
    return jdbc.sql("select " + COLUMNS + FROM + " where r.id = :id")
        .param("id", recordId)
        .query(RecordQueryAdapter::toView)
        .optional();
  }

  @Override
  public Map<AttendanceStatus, Long> countByStatus(RecordFilter filter) {
    Where where = where(filter);
    Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
    jdbc.sql("select r.status, count(*)" + FROM + where.sql() + " group by r.status")
        .params(where.params())
        .query(
            (rs, row) -> {
              counts.put(AttendanceStatus.valueOf(rs.getString(1)), rs.getLong(2));
              return null;
            })
        .list();
    return counts;
  }

  private record Where(String sql, Map<String, Object> params) {}

  private static Where where(RecordFilter filter) {
    List<String> conditions = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    if (filter.branchId() != null) {
      conditions.add("s.branch_id = :branchId");
      params.put("branchId", filter.branchId());
    }
    if (filter.teacherId() != null) {
      conditions.add("s.teacher_id = :teacherId");
      params.put("teacherId", filter.teacherId());
    }
    if (filter.studentId() != null) {
      conditions.add("r.student_id = :studentId");
      params.put("studentId", filter.studentId());
    }
    if (filter.classroomId() != null) {
      conditions.add("s.classroom_id = :classroomId");
      params.put("classroomId", filter.classroomId());
    }
    if (filter.from() != null) {
      conditions.add("s.session_date >= :from");
      params.put("from", filter.from());
    }
    if (filter.to() != null) {
      conditions.add("s.session_date <= :to");
      params.put("to", filter.to());
    }
    String sql = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    return new Where(sql, params);
  }

  private static AttendanceRecordView toView(ResultSet rs, int row) throws SQLException {
    return new AttendanceRecordView(
        rs.getObject("id", UUID.class),
        rs.getObject("session_id", UUID.class),
        rs.getObject("student_id", UUID.class),
        rs.getObject("classroom_id", UUID.class),
        rs.getObject("subject_id", UUID.class),
        rs.getObject("session_date", LocalDate.class),
        rs.getObject("starts_at", LocalTime.class),
        rs.getObject("ends_at", LocalTime.class),
        AttendanceStatus.valueOf(rs.getString("status")),
        rs.getObject("excuse_id", UUID.class),
        rs.getString("note"),
        rs.getObject("marked_at", OffsetDateTime.class).toInstant(),
        rs.getObject("marked_by", UUID.class));
  }
}
