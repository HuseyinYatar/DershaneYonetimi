import { useAttendanceSummary } from "@/api/queries/attendance";
import { AttendanceLegend } from "@/components/AttendanceLegend";
import { SemicircleGauge } from "@/components/charts/SemicircleGauge";
import { Card, CardHeader } from "@/components/ui/Card";
import { QueryError } from "@/components/ui/EmptyState";
import { GaugeSkeleton } from "@/components/ui/ChartSkeletons";
import { formatInt } from "@/lib/format";
import { attendanceRate, attendanceSegments } from "@/lib/attendance";

export function AttendanceCard({ studentId }: { studentId: string }) {
  const summary = useAttendanceSummary(studentId);
  const rate = attendanceRate(summary.data);

  return (
    <Card className="flex flex-col p-5">
      <CardHeader
        title="Katılım"
        action={
          summary.data && (
            <span className="text-[13px] text-dim">{formatInt(summary.data.total ?? 0)} ders</span>
          )
        }
      />
      {summary.isError ? (
        <QueryError error={summary.error} />
      ) : summary.data ? (
        <>
          <div className="mt-5">
            <SemicircleGauge
              rate={rate}
              segments={attendanceSegments(summary.data)}
              caption={rate === null ? "henüz yoklama yok" : "katılım oranı"}
              ariaLabel={
                rate === null
                  ? "Katılım: henüz yoklama kaydı yok"
                  : `Katılım yüzde ${rate}: ${summary.data.present ?? 0} var, ${summary.data.late ?? 0} geç, ${summary.data.absent ?? 0} yok, ${summary.data.excused ?? 0} izinli`
              }
            />
          </div>
          <div className="mt-6">
            <AttendanceLegend summary={summary.data} />
          </div>
        </>
      ) : (
        <div className="mt-5">
          <GaugeSkeleton />
        </div>
      )}
    </Card>
  );
}
