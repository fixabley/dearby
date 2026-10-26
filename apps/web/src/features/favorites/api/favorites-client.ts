// 즐겨찾기·세션 찜 (contract "즐겨찾기(하트)", "트랙·세션 시간표", web "웹 화면 — 2단계 A"): the account routes of `/api/*`, each refreshing the
// session once on 401 like every account call.
import type { Favorites, FavoriteSession } from "@/lib/models";
import { accountRequest, accountSend } from "@/lib/client";

export type FavoriteKind = "program" | "activity" | "session";
const PATHS: Record<FavoriteKind, string> = {
  program: "/me/favorites/programs",
  activity: "/me/favorites/activities",
  session: "/me/session-favorites",
};

/** `PUT` (without a body: the API's default notify level) or `DELETE`; both are idempotent 204s. */
export const setFavorite = (kind: FavoriteKind, id: number, on: boolean) => accountSend<void>(`${PATHS[kind]}/${id}`, on ? "PUT" : "DELETE");
export const favorites = () => accountRequest<Favorites>("/me/favorites");
export const sessionFavorites = () => accountRequest<{ items: FavoriteSession[] }>("/me/session-favorites");
/** Opening a favorited program on `/favorites` turns its `새 모집` badge off. */
export const programSeen = (id: number) => accountSend<void>(`/me/favorites/programs/${id}/seen`, "POST");
