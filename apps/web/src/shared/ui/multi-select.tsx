"use client";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { cn } from "@/shared/lib/utils";
import { Icon } from "@/shared/ui/icon";

/**
 * Inset-grouped row that opens a checkbox list below it (an iOS menu with checkmarks, as the app's `활동 상태`): the row is a disclosure
 * button (`aria-expanded`) showing `label` and the `summary` of the chosen values; the list is native checkboxes in a labelled group, so
 * screen readers and the keyboard need nothing custom. Choosing keeps the list open; a click outside or Escape closes it.
 */
export function MultiSelect<T extends string>({
  label,
  summary,
  options,
  value,
  onChange,
  className,
}: {
  label: string;
  summary: string;
  /** `icon` is an optional glyph before the label (hidden from screen readers; the label is read). */
  options: { value: T; label: string; icon?: ReactNode }[];
  value: T[];
  onChange: (value: T[]) => void;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const id = useId();
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      setOpen(false);
      button.current?.focus();
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);
  return (
    <div ref={root} className={cn("relative", className)}>
      <button
        ref={button}
        type="button"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-11 w-full items-center gap-1.5 rounded-[14px] bg-card px-4 py-1.5 text-left"
      >
        <span className="flex-1 text-body">{label}</span>
        <span className="truncate text-body text-secondary-label">{summary}</span>
        <span className="flex text-secondary-label" aria-hidden="true">
          <Icon name="chevron-down" size="inline" />
        </span>
      </button>
      {open && (
        <fieldset
          id={id}
          className="absolute right-0 z-20 mt-1 flex min-w-56 flex-col rounded-[14px] bg-card py-1 shadow-[0_10px_30px_rgb(0_0_0/0.18)]"
        >
          <legend className="sr-only">{label}</legend>
          {options.map((option) => (
            <label key={option.value} className="flex min-h-11 cursor-pointer items-center gap-3 px-4">
              <input
                type="checkbox"
                className="size-4 accent-teal-fill"
                checked={value.includes(option.value)}
                onChange={(event) =>
                  onChange(
                    event.target.checked
                      ? options.map((item) => item.value).filter((item) => item === option.value || value.includes(item))
                      : value.filter((item) => item !== option.value),
                  )
                }
              />
              {option.icon && <span className="flex text-secondary-label">{option.icon}</span>}
              <span className="text-body">{option.label}</span>
            </label>
          ))}
        </fieldset>
      )}
    </div>
  );
}
