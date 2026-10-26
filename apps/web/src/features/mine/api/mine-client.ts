// 내 활동 (contract "내 활동 서버 저장·일정 상태", web 2단계 B): the account routes of `/api/*`, with the refresh-once wrapper.
import { accountRequest, accountSend, request } from "@/lib/client";
import type { Activity, FavoriteSession, MyActivity, StatusPick } from "@/lib/models";

export const listMine = () => accountRequest<{ items: MyActivity[] }>("/me/activities");
/** Idempotent: an activity already in 내 활동 comes back as it is. */
export const addMine = (activityId: number) => accountSend<MyActivity>(`/me/activities/${activityId}`, "PUT");
export const removeMine = (activityId: number) => accountSend<void>(`/me/activities/${activityId}`, "DELETE");
export const markStage = (activityId: number, scheduleId: number, pick: StatusPick) =>
  accountSend<MyActivity>(`/me/activities/${activityId}/schedules/${scheduleId}`, "PUT", pick);
/** 찜한 세션, read for the 오늘 내 행사 card only (찜 itself is in the apps). */
export const listSessionFavorites = () => accountRequest<{ items: FavoriteSession[] }>("/me/session-favorites");
/** The public activity behind a 내 활동 item; the same response the detail page reads. */
export const fetchActivity = (activityId: number, signal?: AbortSignal) => request<Activity>(`/activities/${activityId}`, { signal });
