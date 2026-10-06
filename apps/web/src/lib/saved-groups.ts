import type { Card, GuestShare } from "./models";

export const noActivityGroup = "none";

export type SavedGroup = { id: string; title: string; cards: Card[] };

/**
 * Same rule as the mobile wallet: a card appears under every activity its
 * shares carry, and cards without any go to a trailing "활동 없음" group.
 * Groups follow the order activities were first saved; empty groups are omitted.
 */
export function savedByActivity(
  items: Card[],
  shares: GuestShare[],
): SavedGroup[] {
  const titles = new Map<string, string>();
  const members = new Map<string, Set<string>>();
  for (const share of [...shares].sort((a, b) =>
    a.savedAt.localeCompare(b.savedAt),
  ))
    for (const activity of share.activities) {
      if (!titles.has(activity.id)) titles.set(activity.id, activity.title);
      members.set(
        activity.id,
        (members.get(activity.id) ?? new Set()).add(share.cardId),
      );
    }
  const grouped = new Set([...members.values()].flatMap((ids) => [...ids]));
  const groups = [...titles].map(([id, title]) => ({
    id,
    title,
    cards: items.filter((card) => members.get(id)!.has(card.id)),
  }));
  groups.push({
    id: noActivityGroup,
    title: "활동 없음",
    cards: items.filter((card) => !grouped.has(card.id)),
  });
  return groups.filter((group) => group.cards.length);
}
