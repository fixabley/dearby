"use client";
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { cn } from "@/shared/lib/utils";
import { Icon, type IconName } from "./icon";

export type MenuItem = { href: string; label: string; icon: IconName; badge?: number };

/**
 * iOS `Menu` / Material `DropdownMenu` on the web (contract "프로필 읽기/수정 모드·상단 바 메뉴"): an icon button that opens a list of
 * icon + label links under it. Closes on a choice, a click outside or Escape. The current page's item is marked.
 */
export function DropdownMenu({ label, icon, items, current, className }: { label: string; icon: IconName; items: MenuItem[]; current?: string; className?: string }) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const id = useId();
  useEffect(() => {
    if (!open) return;
    const away = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", away);
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("pointerdown", away);
      document.removeEventListener("keydown", key);
    };
  }, [open]);
  return (
    <div ref={root} className={cn("relative", className)}>
      <button
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={id}
        className="inline-flex size-11 items-center justify-center text-teal active:opacity-70"
        onClick={() => setOpen((value) => !value)}
      >
        <Icon name={icon} />
      </button>
      {open && (
        <ul
          id={id}
          role="menu"
          aria-label={label}
          className="absolute right-0 top-full z-50 mt-1 w-56 overflow-hidden rounded-[14px] bg-card py-1 shadow-[0_8px_32px_rgb(0_0_0/0.18)] animate-in fade-in-0 zoom-in-95"
        >
          {items.map((item) => (
            <li key={item.href} role="none">
              <Link
                role="menuitem"
                href={item.href}
                aria-current={current === item.href ? "page" : undefined}
                className="flex min-h-11 items-center gap-3 px-4 text-body text-label active:bg-fill aria-[current=page]:font-semibold"
                onClick={() => setOpen(false)}
              >
                <Icon name={item.icon} tone="secondary" />
                <span className="flex-1">{item.label}</span>
                {item.badge ? (
                  <span className="rounded-full bg-teal-fill px-1.5 text-caption font-semibold text-on-teal">{item.badge > 9 ? "9+" : item.badge}</span>
                ) : null}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
