// Horizontal percentage bar; shared by the student result tables and the teacher exam analysis.
export function Bar({
  percent,
  accent = false,
  delay,
}: {
  percent: number;
  accent?: boolean;
  delay?: number; // ms; when set, the fill grows in from the left
}) {
  const clamped = Math.min(100, Math.max(0, percent));
  return (
    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#ecEEF1]">
      <i
        className={`block h-full rounded-full ${accent ? "bg-brand" : "bg-ink"} ${delay === undefined ? "" : "anim-grow-x"}`}
        style={{
          width: `${clamped}%`,
          animationDelay: delay === undefined ? undefined : `${delay}ms`,
        }}
      />
    </div>
  );
}
