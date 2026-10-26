"use client";
import { useEffect, useRef, useState } from "react";
import { RequestError } from "@/lib/client";
import { track } from "@/shared/lib/analytics";
import { useAuth } from "@/features/auth/model/auth-context";
import { type FavoriteKind, setFavorite } from "../api/favorites-client";

/**
 * One heart's state (contract "즐겨찾기(하트)"·"트랙·세션 시간표", web "웹 화면 — 2단계 A"): starts from the server's `favorited`
 * (false on anonymous responses) and follows it when the item reloads; a tap signed out opens the sign-in sheet and flips after
 * sign-in and consent (`requireAccount`). The flip is optimistic — a failed request puts it back and says so in the shared toast.
 * Success sends `favorite_add`/`favorite_remove` with `type`, `id` and, for a session, its `activity_id` (analytics-v1.md "웹").
 */
export function useFavorite(
  kind: FavoriteKind,
  id: number,
  favorited: boolean,
  params: { activity_id?: number; category?: string } = {},
  onChange?: (on: boolean) => void,
) {
  const { requireAccount, signedOutLocally, notify } = useAuth();
  const [on, setOn] = useState(favorited);
  const [busy, setBusy] = useState(false);
  const latest = useRef(favorited);
  useEffect(() => {
    if (latest.current !== favorited) {
      latest.current = favorited;
      setOn(favorited);
    }
  }, [favorited]);
  const { activity_id, category } = params;
  function toggle() {
    requireAccount(async () => {
      if (busy) return;
      const next = !on;
      setOn(next);
      setBusy(true);
      try {
        await setFavorite(kind, id, next);
        onChange?.(next);
        track(next ? "favorite_add" : "favorite_remove", {
          type: kind,
          id,
          ...(activity_id !== undefined && { activity_id }),
          ...(category && { category: category.toLowerCase() }),
        });
      } catch (error) {
        setOn(!next);
        if (error instanceof RequestError && error.status === 401) signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
        else if (error instanceof RequestError && error.status === 404) notify(next ? "더 이상 공개되지 않는 항목이에요." : "이미 즐겨찾기에서 빠진 항목이에요.");
        else notify(next ? "즐겨찾기에 넣지 못했어요. 잠시 후 다시 시도해 주세요." : "즐겨찾기를 해제하지 못했어요. 잠시 후 다시 시도해 주세요.");
      } finally {
        setBusy(false);
      }
    });
  }
  return { on, busy, toggle };
}
