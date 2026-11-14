import { useId } from "react";
import { GAUGE_PATH, gaugeDash, gaugeSegments } from "./gauge";

// 180-degree arc. Without `segments`: grey track plus one green fill, round caps. `rate` is the centered percentage or null (no data).
// With `segments` the arc is split by status into touching butt-capped pieces; only the two arc
// ends are rounded (a disc in the first/last color).
export function SemicircleGauge({
  rate,
  caption,
  ariaLabel,
  segments,
}: {
  rate: number | null;
  caption: string;
  ariaLabel: string;
  segments?: { value: number; color: string }[];
}) {
  const mask = useId();
  const dash = segments || rate === null ? null : gaugeDash(rate);
  const laid = segments ? gaugeSegments(segments.map((s) => s.value)) : [];
  const drawn = laid.flatMap((segment, i) => (segment ? [i] : []));
  const first = drawn[0];
  const last = drawn[drawn.length - 1];
  return (
    <svg
      viewBox="0 0 260 130"
      role="img"
      aria-label={ariaLabel}
      className="mx-auto block w-full max-w-[260px]"
    >
      {drawn.length === 0 && (
        <path
          d={GAUGE_PATH}
          pathLength={100}
          fill="none"
          stroke="#EEF0F3"
          strokeWidth={14}
          strokeLinecap="round"
        />
      )}
      {/* The mask sweeps along the arc, so fill, pieces and end discs all reveal in order. */}
      <mask id={mask} maskUnits="userSpaceOnUse" x={0} y={0} width={260} height={130}>
        <path
          className="anim-draw"
          d={GAUGE_PATH}
          pathLength={100}
          fill="none"
          stroke="#fff"
          strokeWidth={30}
          strokeLinecap="round"
        />
      </mask>
      <g mask={`url(#${mask})`}>
        {dash && (
          <path
            d={GAUGE_PATH}
            pathLength={100}
            fill="none"
            style={{ stroke: "var(--status-success-strong)" }}
            strokeWidth={14}
            strokeLinecap="round"
            strokeDasharray={dash}
          />
        )}
        {laid.map(
          (segment, i) =>
            segment && (
              // Each piece runs 0.3 units into the next one (painted after it) so no seam shows.
              <path
                key={i}
                d={GAUGE_PATH}
                pathLength={100}
                fill="none"
                strokeWidth={14}
                style={{ stroke: segments![i].color }}
                strokeDasharray={`${segment.length + (i === last ? 0 : 0.3)} 100`}
                strokeDashoffset={-segment.start}
              />
            ),
        )}
        {first !== undefined && (
          <>
            <circle cx={14} cy={123} r={7} style={{ fill: segments![first].color }} />
            <circle cx={246} cy={123} r={7} style={{ fill: segments![last].color }} />
          </>
        )}
      </g>
      <text
        x={130}
        y={97}
        textAnchor="middle"
        fontSize={54}
        letterSpacing={-2.5}
        fill="#0B0B0F"
        fontWeight={300}
      >
        {rate === null ? (
          "—"
        ) : (
          <>
            <tspan fill="#6B7280" fontSize={30} dx={-6}>
              %
            </tspan>
            <tspan dx={4}>{rate}</tspan>
          </>
        )}
      </text>
      <text x={130} y={119} textAnchor="middle" fontSize={13} fill="#636B78">
        {caption}
      </text>
    </svg>
  );
}
