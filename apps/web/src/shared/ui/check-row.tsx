import { cn } from "@/shared/lib/utils";

/**
 * A checkbox row of an inset group (contract "폼은 플랫폼 기본 컴포넌트", "토글 라벨은 바로 옆"): the native checkbox in the accent
 * colour, 44px tall, and the whole row is the label.
 */
export function CheckRow({
  checked,
  onChange,
  disabled,
  className,
  children,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <label className={cn("flex min-h-11 cursor-pointer items-center gap-3 px-4 py-2 text-body", disabled && "opacity-50", className)}>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
        className="size-5 shrink-0 accent-teal-fill"
      />
      <span className="flex-1 break-keep">{children}</span>
    </label>
  );
}
