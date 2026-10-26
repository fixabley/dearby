import { type Progress, progressLabel } from "@/features/mine/model/stages";
import { cn } from "@/shared/lib/utils";

const MAX_SEGMENTS = 4;

/**
 * Segment track of an applied activity (contract "내 활동 진행 트랙" › 화면, iOS `StageTrack`): up to four segments — past filled
 * (teal), current outlined, upcoming grey, 탈락 red — with `+N` beyond and the names underneath (the current one bold). One
 * accessibility element ("5단계 중 3단계 1차 면접 진행 중"); nothing animates.
 */
export function StageTrack({ progress }: { progress: Progress }) {
  const shown = progress.stages.slice(0, MAX_SEGMENTS);
  const hidden = progress.stages.length - shown.length;
  return (
    <div role="img" aria-label={progressLabel(progress)} className="flex flex-col gap-1.5">
      <div className="flex items-center gap-1">
        {shown.map((stage) => (
          <span
            key={stage.id}
            className={cn(
              "h-1.5 flex-1 rounded-full",
              stage.mark.kind === "FAILED"
                ? "bg-danger"
                : stage.state === "past"
                  ? "bg-teal-fill"
                  : stage.state === "current"
                    ? "border-[1.5px] border-teal"
                    : "bg-fill",
            )}
          />
        ))}
        {hidden > 0 && <span className="shrink-0 text-caption2 text-secondary-label tabular-nums">+{hidden}</span>}
      </div>
      <div className="flex gap-1 text-caption2 text-secondary-label">
        {shown.map((stage) => (
          <span key={stage.id} className={cn("min-w-0 flex-1 truncate", stage.state === "current" && "font-semibold text-label")}>
            {stage.name}
          </span>
        ))}
        {hidden > 0 && <span className="w-[2ch] shrink-0" />}
      </div>
    </div>
  );
}
