import type { components } from "@/api/schema/exam-analysis";
import { LineChart } from "@/components/charts/LineChart";
import { formatNet } from "@/lib/format";
import { toLinePoints } from "@/lib/progress";

type ProgressPoint = components["schemas"]["ProgressPoint"];

// Net-over-time line for one exam type; the last point carries the exam name as a chip.
export function NetProgress({ points }: { points: ProgressPoint[] }) {
  const first = points[0];
  const last = points[points.length - 1];
  const label =
    points.length > 1
      ? `Net gelişimi: ${formatNet(first.net)} değerinden ${formatNet(last.net)} değerine, ${points.length} deneme`
      : `Net gelişimi: ${last.examName}, ${formatNet(last.net)} net`;
  return <LineChart points={toLinePoints(points)} ariaLabel={label} />;
}
