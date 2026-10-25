"use client";
import { useLayoutEffect, useRef, useState } from "react";
import { cn } from "@/shared/lib/utils";
import { Badge } from "./badge";
import { Icon, type IconName } from "./icon";

/** A tag as the icon components need it; structural so `shared` does not import the API models. */
type TagLike = { id: number; name: string; nameEn?: string | null };

/**
 * 태그 아이콘 매핑 (contract "분야·성격 태그와 아이콘", 2026-10-24): `nameEn` → the glyph shaped like the iOS SF Symbol in the
 * contract table (plus 공학 `engineering`, 웹·앱 `webApp`, 백엔드 `backend`, 게임 `game`, 오픈소스 `openSource`). An unknown `nameEn` (a tag the operator added later) gets the default `tag` glyph.
 */
const tagIcons: Record<string, IconName> = {
  startup: "lightbulb",
  project: "hammer",
  mentoring: "bubbles",
  networking: "network",
  funding: "won",
  it: "laptop",
  dataAi: "sparkles",
  cloud: "cloud",
  semiconductor: "cpu",
  mechanical: "gear",
  civilEngineering: "building",
  security: "lock-shield",
  business: "chart-bar",
  marketing: "megaphone",
  investing: "trend-up",
  volunteering: "heart",
  reading: "book",
  video: "video",
  engineering: "tools",
  webApp: "devices",
  backend: "server",
  game: "gamepad",
  openSource: "github", // the `</>` code-brackets glyph
};

/** The tag's glyph at the surrounding font size (`size="inline"`). */
export function TagIcon({ nameEn }: { nameEn?: string | null }) {
  return <Icon name={(nameEn && tagIcons[nameEn]) || "tag"} size="inline" />;
}

/** Room kept for the `+n` chip while deciding how many chips fit. */
const MORE_WIDTH = 40;

/**
 * 카드 태그 줄 (contract "태그 아이콘+라벨·분야 1개·자동 펼침 없음"): one line of icon+text chips — the single 분야 chip (mint, teal)
 * first, then 성격 chips (system fill). Chips that do not fit are hidden behind `+n`; the line never wraps. Every chip stays
 * rendered so it can be measured; the hidden ones are `invisible`, which also keeps them out of the accessibility tree.
 */
export function TagChipRow({ field, traits, className }: { field?: TagLike; traits: TagLike[]; className?: string }) {
  const chips = [...(field ? [{ tag: field, field: true }] : []), ...traits.map((tag) => ({ tag, field: false }))];
  const row = useRef<HTMLSpanElement>(null);
  const [fit, setFit] = useState(chips.length);
  useLayoutEffect(() => {
    const element = row.current;
    if (!element) return;
    const measure = () => {
      const widths = [...element.querySelectorAll<HTMLElement>("[data-chip]")].map((chip) => chip.offsetWidth);
      const gap = parseFloat(getComputedStyle(element).columnGap) || 0;
      const available = element.clientWidth;
      let used = 0;
      let count = 0;
      for (const [index, width] of widths.entries()) {
        used += width + (index ? gap : 0);
        const reserve = index < widths.length - 1 ? gap + MORE_WIDTH : 0;
        if (used + reserve > available) break;
        count = index + 1;
      }
      setFit(Math.max(1, count));
    };
    measure();
    // Chips are observed too: their width changes when the web font arrives while the line keeps its width.
    const observer = new ResizeObserver(measure);
    for (const each of [element, ...element.querySelectorAll("[data-chip]")]) observer.observe(each);
    return () => observer.disconnect();
  }, [chips.length]);
  if (!chips.length) return null;
  const hidden = chips.length - fit;
  return (
    <span ref={row} className={cn("flex min-w-0 flex-nowrap gap-1.5 overflow-hidden", className)}>
      {chips.map(({ tag, field }, index) => (
        <Badge
          key={tag.id}
          data-chip
          variant={field ? "tinted" : "neutral"}
          className={cn("font-medium", index >= fit && "invisible order-2")}
        >
          <span aria-hidden="true" className="flex">
            <TagIcon nameEn={tag.nameEn} />
          </span>
          {tag.name}
        </Badge>
      ))}
      {hidden > 0 && (
        <Badge variant="neutral" className="order-1 font-medium">
          <span aria-hidden="true">+{hidden}</span>
          <span className="sr-only">외 {hidden}개</span>
        </Badge>
      )}
    </span>
  );
}
