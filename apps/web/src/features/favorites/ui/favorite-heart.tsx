"use client";
import { HeartButton } from "@/shared/ui/heart-button";
import type { FavoriteKind } from "../api/favorites-client";
import { useFavorite } from "../model/use-favorite";

const LABELS: Record<FavoriteKind, readonly [string, string]> = {
  program: ["즐겨찾기", "즐겨찾기 해제"],
  activity: ["즐겨찾기", "즐겨찾기 해제"],
  session: ["찜", "찜 해제"],
};

/**
 * The heart on a program card, an activity card or detail, or a session card (contract "즐겨찾기(하트)"·"트랙·세션 시간표"). `onChange`
 * lets the owner mirror the state (the timetable's 내 시간표 view, the `/favorites` lists).
 */
export function FavoriteHeart({
  kind,
  id,
  favorited,
  activityId,
  category,
  size,
  className,
  onChange,
}: {
  kind: FavoriteKind;
  id: number;
  favorited: boolean;
  /** The session's activity, for `favorite_add` on sessions. */
  activityId?: number;
  /** The activity's 종류 code, lowercased in the event like `activity_view`. */
  category?: string | null;
  size?: "default" | "sm";
  className?: string;
  onChange?: (on: boolean) => void;
}) {
  const { on, busy, toggle } = useFavorite(kind, id, favorited, { activity_id: activityId, category: category ?? undefined }, onChange);
  return <HeartButton on={on} labels={LABELS[kind]} onToggle={toggle} disabled={busy} size={size} className={className} />;
}
