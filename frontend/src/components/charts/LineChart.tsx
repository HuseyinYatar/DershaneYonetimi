import { useEffect, useRef, useState } from "react";

export interface LinePoint {
  value: number;
  // Axis tick text (a month); consecutive points with the same tick show it once.
  tick: string;
  // Tooltip chip on the last point.
  name: string;
}

// Width of the container in px (falls back to a sane size before layout and in tests).
function useElementWidth(fallback: number) {
  const ref = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(fallback);
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(([entry]) => {
      const w = Math.round(entry.contentRect.width);
      if (w > 0) setWidth(w);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return [ref, width] as const;
}

// Catmull-Rom spline converted to cubic beziers (smooth line through every point).
export function smoothPath(points: { x: number; y: number }[]): string {
  if (points.length === 0) return "";
  if (points.length === 1) return `M${points[0].x} ${points[0].y}`;
  let d = `M${points[0].x.toFixed(1)} ${points[0].y.toFixed(1)}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i - 1] ?? points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2] ?? p2;
    const c1x = p1.x + (p2.x - p0.x) / 6;
    const c1y = p1.y + (p2.y - p0.y) / 6;
    const c2x = p2.x - (p3.x - p1.x) / 6;
    const c2y = p2.y - (p3.y - p1.y) / 6;
    d += `C${c1x.toFixed(1)} ${c1y.toFixed(1)} ${c2x.toFixed(1)} ${c2y.toFixed(1)} ${p2.x.toFixed(1)} ${p2.y.toFixed(1)}`;
  }
  return d;
}

export function LineChart({ points, ariaLabel }: { points: LinePoint[]; ariaLabel: string }) {
  const [ref, width] = useElementWidth(600);
  const height = width < 500 ? 170 : 210;
  const last = points[points.length - 1];
  const chipWidth = last ? Math.min(Math.max(last.name.length * 7 + 24, 80), 150) : 80;
  const padLeft = 14;
  const padRight = chipWidth / 2 + 6;
  const top = 50;
  const bottom = height - 30;

  const values = points.map((p) => p.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const xs = points.map((_, i) =>
    points.length === 1
      ? width - padRight
      : padLeft + (i * (width - padLeft - padRight)) / (points.length - 1),
  );
  const ys = values.map((v) => bottom - ((v - min) / span) * (bottom - top));
  const coords = xs.map((x, i) => ({ x, y: ys[i] }));
  const lx = xs[xs.length - 1];
  const ly = ys[ys.length - 1];
  const chipX = Math.min(Math.max(lx - chipWidth / 2, 0), width - chipWidth);
  const gridY = [top + 14, (top + bottom) / 2, bottom + 8];

  return (
    <div ref={ref} className="w-full">
      {points.length > 0 && (
        <svg
          viewBox={`0 0 ${width} ${height}`}
          role="img"
          aria-label={ariaLabel}
          className="block w-full"
        >
          {gridY.map((y) => (
            <line key={y} x1={0} y1={y} x2={width} y2={y} stroke="#EEF0F3" />
          ))}
          {points.length > 1 && (
            <path
              className="anim-draw"
              d={smoothPath(coords)}
              pathLength={100}
              fill="none"
              stroke="#6D4AFF"
              strokeWidth={2.25}
              strokeLinecap="round"
            />
          )}
          <g className="anim-fade" style={{ animationDelay: "900ms" }}>
            <line
              x1={lx}
              y1={38}
              x2={lx}
              y2={bottom + 12}
              stroke="#A9AEBA"
              strokeWidth={1.25}
              strokeDasharray="2 4"
              strokeLinecap="round"
            />
            <circle cx={lx} cy={ly} r={5.5} fill="#fff" stroke="#6D4AFF" strokeWidth={2.25} />
            <rect
              x={chipX}
              y={6}
              width={chipWidth}
              height={30}
              rx={10}
              fill="#fff"
              stroke="#DEE0E5"
            />
            <text
              x={chipX + chipWidth / 2}
              y={25}
              textAnchor="middle"
              fontSize={12.5}
              fontWeight={500}
              fill="#0B0B0F"
            >
              {last.name.length > 18 ? `${last.name.slice(0, 17)}…` : last.name}
            </text>
          </g>
          {points.map((p, i) =>
            i === 0 || p.tick !== points[i - 1].tick ? (
              <text
                key={i}
                x={Math.max(xs[i], 14)}
                y={height - 8}
                textAnchor={i === 0 ? "start" : "middle"}
                fontSize={12}
                fill="#636B78"
              >
                {p.tick}
              </text>
            ) : null,
          )}
        </svg>
      )}
    </div>
  );
}
