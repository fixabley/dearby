import { Icon, type IconName } from "./icon";

/**
 * 활동 종류 아이콘 (contract "아이콘 통일" 의미별 표의 활동 종류, 2026-10-26): the API code → the glyph shaped like the iOS SF Symbol.
 * Keyed by the code string so `shared` does not import the API models; an unknown code has no glyph.
 */
const categoryIcons: Record<string, IconName> = {
  BOOTCAMP: "graduation-cap",
  CONFERENCE: "people",
  WORKSHOP: "pencil-ruler",
  STUDY: "books",
  CLUB: "people-pair",
  LECTURE: "mic",
  HACKATHON: "keyboard",
  CONTEST: "trophy",
  MEETUP: "cup",
  OTHER: "grid",
};

/** The category's glyph at the surrounding font size, hidden from screen readers (the name beside it is read); nothing for an unknown code. */
export function CategoryIcon({ code }: { code: string }) {
  const name = categoryIcons[code];
  if (!name) return null;
  return (
    <span aria-hidden="true" className="flex">
      <Icon name={name} size="inline" />
    </span>
  );
}
