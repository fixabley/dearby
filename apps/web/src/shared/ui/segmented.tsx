import { cn } from "@/shared/lib/utils";

/** iOS segmented control: equal segments on a system-fill track, the selected one raised on the card colour. */
export function Segmented<T extends string>({
  label,
  options,
  value,
  onChange,
  className,
}: {
  label: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}) {
  return (
    <div role="radiogroup" aria-label={label} className={cn("flex rounded-[9px] bg-fill p-0.5", className)}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={option.value === value}
          className={cn(
            "min-h-8 flex-1 rounded-[7px] text-subheadline font-semibold",
            option.value === value ? "bg-card shadow-[0_3px_8px_rgb(0_0_0/0.12)]" : "text-secondary-label",
          )}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
