import { useId } from "react";
import { formatNet } from "@/lib/format";

export interface Bar {
  label: string;
  value: number;
  // Violet bar with a chip, e.g. "+%10" (the most improved subject).
  chip?: string;
  accent?: boolean;
  // Solid ink bar; the rest are hatched.
  strong?: boolean;
}

const W = 320;
const TOP = 30;
const BASE = 184;

// Per-subject nets: ink for the strongest, violet for the most improved, hatched for the rest.
export function BarChart({
  bars,
  max,
  ariaLabel,
}: {
  bars: Bar[];
  max: number;
  ariaLabel: string;
}) {
  const hatch = useId();
  const slot = W / Math.max(bars.length, 1);
  const scale = (BASE - TOP) / Math.max(max, 1);

  return (
    <svg viewBox="0 0 320 228" role="img" aria-label={ariaLabel} className="block w-full">
      <defs>
        <pattern
          id={hatch}
          width={7}
          height={7}
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(45)"
        >
          <rect width={7} height={7} fill="#EDEEF2" />
          <line x1={0} y1={0} x2={0} y2={7} stroke="#D2D5DC" strokeWidth={2.2} />
        </pattern>
      </defs>
      {bars.map((bar, i) => {
        const x = i * slot + 5;
        const width = slot - 8;
        const height = Math.max(bar.value, 0) * scale;
        const top = BASE - height;
        const r = Math.min(13, width / 2, height);
        const fill = bar.accent ? "#6D4AFF" : bar.strong ? "#0B0B0F" : `url(#${hatch})`;
        const words = bar.label.split(" ");
        // Inside the bar's top edge, clear of the value label; a short bar keeps it above the baseline.
        const chipY = Math.min(top + 26, BASE - 26);
        return (
          <g key={bar.label}>
            <line x1={x - 4.5} y1={6} x2={x - 4.5} y2={BASE} stroke="#DEE0E5" />
            {height > 0 && (
              <path
                d={`M${x} ${top + r}a${r} ${r} 0 0 1 ${r} ${-r}h${width - 2 * r}a${r} ${r} 0 0 1 ${r} ${r}V${BASE}H${x}Z`}
                fill={fill}
                className="anim-grow-y"
                style={{ animationDelay: `${i * 80}ms` }}
              />
            )}
            <text
              x={x + 4}
              y={22}
              fontSize={14}
              fontWeight={500}
              fill="#0B0B0F"
              letterSpacing={-0.2}
            >
              {formatNet(bar.value)}
            </text>
            {/* A two-word subject name stacks on two lines so it cannot run into the next column. */}
            <text x={x + 4} y={206} fontSize={12} fill="#636B78">
              {words.length > 1 ? (
                <>
                  <tspan x={x + 4}>{words[0]}</tspan>
                  <tspan x={x + 4} dy={15}>
                    {words.slice(1).join(" ")}
                  </tspan>
                </>
              ) : (
                bar.label
              )}
            </text>
            {bar.chip && (
              <g className="anim-fade" style={{ animationDelay: `${i * 80 + 600}ms` }}>
                <title>Önceki denemeye göre değişim</title>
                <circle
                  cx={x + width / 2}
                  cy={chipY}
                  r={24}
                  fill="#fff"
                  stroke="#6D4AFF"
                  strokeWidth={1.5}
                />
                <text
                  x={x + width / 2}
                  y={chipY + 4}
                  textAnchor="middle"
                  fontSize={12}
                  fontWeight={500}
                  fill="#4A2FD1"
                >
                  {bar.chip}
                </text>
              </g>
            )}
          </g>
        );
      })}
    </svg>
  );
}
