import type { components } from "@/api/schema/exam-analysis";
import type { Bar } from "@/components/charts/BarChart";

type Detail = components["schemas"]["StudentResultDetail"];
type ProgressPoint = components["schemas"]["ProgressPoint"];

// Strongest subject in ink; the subject with the largest net gain vs the previous exam in violet.
export function subjectBars(detail: Detail, points: ProgressPoint[]): { bars: Bar[]; max: number } {
  const previous = points.length >= 2 ? points[points.length - 2] : undefined;
  const isLatest = points[points.length - 1]?.mockExamId === detail.mockExamId;
  const nets = detail.subjects.map((s) => Number(s.net));
  const strongest = nets.indexOf(Math.max(...nets));

  // The strongest subject keeps the ink bar, so the violet one is the most improved of the rest.
  let improved = -1;
  let bestGain = 0;
  let chip: string | undefined;
  if (previous && isLatest) {
    detail.subjects.forEach((subject, i) => {
      if (i === strongest && nets.length > 1) return;
      const before = Number(previous.subjects.find((s) => s.subjectId === subject.subjectId)?.net);
      const gain = nets[i] - before;
      if (Number.isFinite(before) && before !== 0 && gain > bestGain) {
        bestGain = gain;
        improved = i;
        chip = `+%${Math.round((gain / Math.abs(before)) * 100)}`;
      }
    });
  }

  const bars = detail.subjects.map<Bar>((subject, i) => ({
    label: subject.subjectName,
    value: nets[i],
    strong: i === strongest && i !== improved,
    accent: i === improved,
    chip: i === improved ? chip : undefined,
  }));
  const max = Math.max(...detail.subjects.map((s) => s.correct + s.wrong + s.blank), 1);
  return { bars, max };
}
