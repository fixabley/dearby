import { cn } from "@/shared/lib/utils";
import { Icon, type IconName } from "./icon";

/** A tag as the icon components need it; structural so `shared` does not import the API models. */
type TagLike = { id: number; name: string; nameEn?: string | null };

/**
 * 태그 아이콘 매핑 (contract "분야·성격 태그와 아이콘", 2026-10-24): `nameEn` → the glyph shaped like the iOS SF Symbol in the
 * contract table (plus 공학 `engineering`, 웹·앱 `webApp`, 게임 `game`, 오픈소스 `openSource`). An unknown `nameEn` (a tag the operator added later) gets the default `tag` glyph.
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
  game: "gamepad",
  openSource: "github", // the `</>` code-brackets glyph
};

/** The tag's glyph at the surrounding font size (`size="inline"`). */
export function TagIcon({ nameEn }: { nameEn?: string | null }) {
  return <Icon name={(nameEn && tagIcons[nameEn]) || "tag"} size="inline" />;
}

/** 분야 badges stacked under the 44px program logo: up to two 22px mint circles with the teal glyph, 4px apart. */
export function FieldTagBadges({ tags }: { tags: TagLike[] }) {
  return tags.slice(0, 2).map((tag) => (
    <span
      key={tag.id}
      role="img"
      aria-label={`분야: ${tag.name}`}
      title={`분야: ${tag.name}`}
      className="grid size-[22px] place-items-center rounded-full bg-mint text-[13px] text-teal"
    >
      <TagIcon nameEn={tag.nameEn} />
    </span>
  ));
}

/** 얻을 수 있는 것 as a 16px secondary icon row, 8px apart, no text; one accessible name lists them all. Nothing without tags. */
export function TraitIconRow({ tags, className }: { tags: TagLike[]; className?: string }) {
  if (!tags.length) return null;
  return (
    <span
      role="img"
      aria-label={`얻을 수 있는 것: ${tags.map((tag) => tag.name).join(", ")}`}
      className={cn("flex flex-wrap gap-2 text-[16px] text-secondary-label", className)}
    >
      {tags.map((tag) => (
        <span key={tag.id} title={tag.name} className="flex">
          <TagIcon nameEn={tag.nameEn} />
        </span>
      ))}
    </span>
  );
}
