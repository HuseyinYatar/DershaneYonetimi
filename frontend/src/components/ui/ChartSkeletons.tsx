import { GAUGE_PATH } from "@/components/charts/gauge";
import { smoothPath } from "@/components/charts/LineChart";

// Loading placeholders drawn in the shape of the chart they stand in for (same viewBox).
const FILL = "#E4E6EB";

export function GaugeSkeleton() {
  return (
    <svg
      viewBox="0 0 260 130"
      role="status"
      aria-label="Yükleniyor"
      className="mx-auto block w-full max-w-[260px] animate-pulse"
    >
      <path d={GAUGE_PATH} fill="none" stroke={FILL} strokeWidth={14} strokeLinecap="round" />
      <rect x={96} y={62} width={68} height={30} rx={8} fill={FILL} />
      <rect x={108} y={104} width={44} height={10} rx={5} fill={FILL} />
    </svg>
  );
}

const BAR_HEIGHTS = [120, 80, 140, 100, 60];

export function BarsSkeleton() {
  const slot = 320 / BAR_HEIGHTS.length;
  return (
    <svg
      viewBox="0 0 320 228"
      role="status"
      aria-label="Yükleniyor"
      className="block w-full animate-pulse"
    >
      {BAR_HEIGHTS.map((h, i) => {
        const x = i * slot + 5;
        const w = slot - 8;
        return (
          <g key={i}>
            <rect x={x} y={184 - h} width={w} height={h} rx={13} fill={FILL} />
            <rect x={x} y={196} width={w * 0.7} height={9} rx={4.5} fill={FILL} />
          </g>
        );
      })}
    </svg>
  );
}

const WAVE = [0.7, 0.45, 0.6, 0.25, 0.4, 0.1].map((v, i) => ({ x: 14 + i * 114, y: 40 + v * 120 }));

export function LineSkeleton() {
  return (
    <svg
      viewBox="0 0 600 210"
      role="status"
      aria-label="Yükleniyor"
      className="block w-full animate-pulse"
    >
      <path d={smoothPath(WAVE)} fill="none" stroke={FILL} strokeWidth={3} strokeLinecap="round" />
      <circle cx={WAVE[5].x} cy={WAVE[5].y} r={6} fill={FILL} />
    </svg>
  );
}

// Rows like the weak-topic list on the violet card: name, subject, percentage, thin bar.
export function WeakTopicsSkeleton() {
  return (
    <ul role="status" aria-label="Yükleniyor" className="animate-pulse">
      {[0, 1, 2].map((i) => (
        <li key={i} className="border-t border-white/[0.22] py-4 first:border-t-0">
          <div className="flex items-end justify-between gap-3">
            <div className="space-y-2">
              <div className="h-3.5 w-36 rounded-full bg-white/25" />
              <div className="h-3 w-20 rounded-full bg-white/20" />
            </div>
            <div className="h-7 w-14 rounded-lg bg-white/25" />
          </div>
          <div className="mt-3 h-[3px] rounded-full bg-white/25" />
        </li>
      ))}
    </ul>
  );
}
