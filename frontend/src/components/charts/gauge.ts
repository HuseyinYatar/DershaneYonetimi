export const GAUGE_PATH = "M14 123A116 116 0 0 1 246 123";

// The arc has pathLength=100, so a percentage is the dash length directly. Out-of-range input is
// clamped; 0 returns null because a zero-length dash with round caps still paints a dot.
export function gaugeDash(rate: number): string | null {
  const clamped = Math.min(100, Math.max(0, Number.isFinite(rate) ? rate : 0));
  return clamped === 0 ? null : `${clamped} 100`;
}

export interface GaugeSegment {
  start: number;
  length: number;
}

// Lays counts along the 100-long arc in order, touching (gap defaults to 0). A non-zero count
// never gets less than `min` units, so one missed lesson out of 50 stays visible; the larger
// segments give up that room. Zero or invalid counts return null (nothing drawn).
export function gaugeSegments(counts: number[], min = 4.5, gap = 0): (GaugeSegment | null)[] {
  const safe = counts.map((c) => (Number.isFinite(c) && c > 0 ? c : 0));
  const total = safe.reduce((a, b) => a + b, 0);
  const shown = safe.filter((c) => c > 0).length;
  if (total === 0) return safe.map(() => null);

  const room = 100 - gap * (shown - 1);
  const raw = safe.map((c) => (c / total) * room);
  const small = raw.map((l) => l > 0 && l < min);
  const bigSum = raw.reduce((sum, l, i) => (l > 0 && !small[i] ? sum + l : sum), 0);
  const scale = bigSum > 0 ? (room - small.filter(Boolean).length * min) / bigSum : 1;

  let pos = 0;
  return raw.map((l, i) => {
    if (l === 0) return null;
    const length = small[i] ? min : l * scale;
    const segment = { start: pos, length };
    pos += length + gap;
    return segment;
  });
}
