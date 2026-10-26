import { cn } from "@/shared/lib/utils";
import { Icon } from "./icon";

/**
 * 하트 토글 (contract "즐겨찾기(하트)"·"트랙·세션 시간표": iOS `heart`/`heart.fill`): a 44px round target whose glyph is the outline
 * when off and the filled teal heart when on; `aria-pressed` carries the state and `labels` the accessibility name pair
 * (`즐겨찾기`/`즐겨찾기 해제` for programs and activities, `찜`/`찜 해제` for sessions). `size="sm"` is the 36px variant inside cards.
 */
export function HeartButton({
  on,
  labels,
  onToggle,
  disabled,
  size = "default",
  className,
}: {
  on: boolean;
  labels: readonly [off: string, on: string];
  onToggle: () => void;
  disabled?: boolean;
  size?: "default" | "sm";
  className?: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={on}
      aria-label={on ? labels[1] : labels[0]}
      disabled={disabled}
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onToggle();
      }}
      className={cn(
        "grid shrink-0 place-items-center rounded-full transition-[opacity,transform] active:scale-90 active:opacity-70 disabled:pointer-events-none disabled:opacity-50",
        size === "sm" ? "size-9 -m-1.5" : "size-11 -m-2",
        on ? "text-teal" : "text-secondary-label",
        className,
      )}
    >
      <Icon name={on ? "heart-fill" : "heart"} size="row" />
    </button>
  );
}
