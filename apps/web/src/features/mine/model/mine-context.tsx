"use client";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { type Activity, type FavoriteSession, type MyActivity, type ScheduleStatus, activityStatus } from "@/lib/models";
import { RequestError } from "@/lib/client";
import { track } from "@/shared/lib/analytics";
import { Dialog, DialogActions, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { Toast, type Notice } from "@/shared/ui/toast";
import { useAuth } from "@/features/auth/model/auth-context";
import { addMine, fetchActivity, listMine, listSessionFavorites, markStage, removeMine } from "../api/mine-client";
import { type Stage, autopassCandidates, autopassMessage, buildProgress, ended, passedOption } from "./stages";

/** One 내 활동 item with its public activity: `undefined` while loading, `null` when the activity is gone (404 or `deleted`). */
export type MineEntry = { item: MyActivity; activity?: Activity | null };
type Status = "idle" | "loading" | "ready" | "error";
type Loaded = { key: string; status: "ready" | "error"; entries: MineEntry[]; favorites: FavoriteSession[] };
const EMPTY_ENTRIES: MineEntry[] = [];
const EMPTY_FAVORITES: FavoriteSession[] = [];
export type Mine = {
  /** `idle` while signed out (or before consent): nothing is read and nothing is in 내 활동 here. */
  status: Status;
  entries: MineEntry[];
  favorites: FavoriteSession[];
  entry: (activityId: number) => MineEntry | undefined;
  /** `PUT /me/activities/{id}` (idempotent), 사용 통계 `myactivity_add`, then the 통과 제안 once per activity. Throws on failure. */
  add: (activity: Activity, source: "discovery" | "link") => Promise<void>;
  remove: (activityId: number) => Promise<void>;
  /** Marks one 선발 단계 (`stage_mark` first); the server's item replaces the local one. Throws on failure. */
  mark: (activityId: number, stage: Stage, status: ScheduleStatus) => Promise<void>;
  reload: () => void;
};
const MineContext = createContext<Mine | null>(null);

/** 사용 통계 매개변수 (analytics-v1.md): ids and codes only. */
export function activityParams(activity: Activity) {
  return { activity_id: activity.id, ...(activity.category && { category: activity.category.toLowerCase() }) };
}
const pickOf = (status: ScheduleStatus) => (status.id === null ? { kind: status.kind } : { statusId: status.id });
/** Activities already asked the 통과 제안 (contract: asked once, whatever the answer; iOS `dearby.mine.autopassAsked`). */
const ASKED_KEY = "dearby.mine.autopassAsked";
function askedBefore(activityId: number): boolean {
  try {
    const asked: unknown = JSON.parse(localStorage.getItem(ASKED_KEY) ?? "[]");
    if (Array.isArray(asked) && asked.includes(activityId)) return true;
    localStorage.setItem(ASKED_KEY, JSON.stringify([...(Array.isArray(asked) ? asked : []), activityId]));
  } catch {
    // Without storage the question may repeat on a later add; nothing else depends on it.
  }
  return false;
}
type Suggestion = { activity: Activity; stages: Stage[]; remaining: Stage[] };

/**
 * 내 활동 of the signed-in account (contract "내 활동 서버 저장·일정 상태"; web 2단계 B): read once per sign-in (`GET /me/activities`, then each
 * activity and the 찜한 세션 for the 오늘 내 행사 card) and shared by the `+`/check toggles, `/mine`, the progress page and the card, so
 * none of them asks the server again. The web has no device copy: signed out, nothing is in 내 활동. Also owns the 통과 제안 dialog.
 */
export function MineProvider({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  const active = auth.signedIn && !auth.consentRequired;
  const [attempt, setAttempt] = useState(0);
  const [suggestion, setSuggestion] = useState<Suggestion>();
  const [notice, setNotice] = useState<Notice>();
  const { signedOutLocally } = auth;
  const expired = useCallback(() => signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요."), [signedOutLocally]);
  // What one sign-in (and `reload`) read, tagged with its key: a signed-out or reloaded key hides it, so nothing is reset in the effect.
  const key = active ? `on:${attempt}` : "off";
  const [loaded, setLoaded] = useState<Loaded>();
  const live = loaded?.key === key ? loaded : undefined;
  const status: Status = !active ? "idle" : live ? live.status : "loading";
  const entries = live?.entries ?? EMPTY_ENTRIES;
  const favorites = live?.favorites ?? EMPTY_FAVORITES;
  /** Changes the loaded entries, unless they belong to another key by now. */
  const patch = useCallback(
    (change: (entries: MineEntry[]) => MineEntry[]) =>
      setLoaded((previous) => (previous && previous.key === key ? { ...previous, entries: change(previous.entries) } : previous)),
    [key],
  );

  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    Promise.all([listMine(), listSessionFavorites().catch(() => ({ items: [] as FavoriteSession[] }))])
      .then(([mine, sessions]) => {
        if (controller.signal.aborted) return;
        setLoaded({ key, status: "ready", entries: mine.items.map((item) => ({ item, activity: item.deleted ? null : undefined })), favorites: sessions.items });
        for (const item of mine.items) {
          if (item.deleted) continue;
          fetchActivity(item.activityId, controller.signal).then(
            (activity) => patch((list) => list.map((entry) => (entry.item.activityId === item.activityId ? { ...entry, activity } : entry))),
            (error) => {
              if (controller.signal.aborted) return;
              // A withdrawn activity reads as `삭제된 활동`; any other failure leaves the row loading until `reload`.
              if (error instanceof RequestError && error.status === 404)
                patch((list) => list.map((entry) => (entry.item.activityId === item.activityId ? { ...entry, activity: null } : entry)));
            },
          );
        }
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RequestError && error.status === 401) expired();
        else setLoaded({ key, status: "error", entries: [], favorites: [] });
      });
    return () => controller.abort();
  }, [active, key, expired, patch]);

  const replace = useCallback(
    (item: MyActivity, activity?: Activity) =>
      patch((list) => {
        const index = list.findIndex((entry) => entry.item.activityId === item.activityId);
        if (index < 0) return [...list, { item, activity }];
        return list.map((entry, at) => (at === index ? { item, activity: activity ?? entry.activity } : entry));
      }),
    [patch],
  );
  const failed = useCallback(
    (error: unknown): never => {
      if (error instanceof RequestError && error.status === 401) expired();
      throw error;
    },
    [expired],
  );

  const add = useCallback<Mine["add"]>(
    async (activity, source) => {
      const now = Date.now();
      const item = await addMine(activity.id).catch(failed);
      replace(item, activity);
      track("myactivity_add", { ...activityParams(activity), source });
      // 지난 선발 단계 통과 제안: not for a 종료 activity or an ended track, and once per activity.
      const progress = buildProgress(activity, item, now);
      if (activityStatus(activity, now) === "ENDED" || ended(progress)) return;
      const stages = autopassCandidates(progress, now);
      if (!stages.length || askedBefore(activity.id)) return;
      const ids = new Set(stages.map((stage) => stage.id));
      const remaining = progress.stages.filter((stage) => stage.selection && !ids.has(stage.id) && stage.mark.kind !== "PASSED");
      setSuggestion({ activity, stages, remaining });
    },
    [replace, failed],
  );
  const remove = useCallback<Mine["remove"]>(
    async (activityId) => {
      await removeMine(activityId).catch(failed);
      patch((list) => list.filter((entry) => entry.item.activityId !== activityId));
    },
    [failed, patch],
  );
  const mark = useCallback<Mine["mark"]>(
    async (activityId, stage, status) => {
      const entry = entries.find((each) => each.item.activityId === activityId);
      track("stage_mark", { ...(entry?.activity ? activityParams(entry.activity) : { activity_id: activityId }), kind: status.kind.toLowerCase() });
      const item = await markStage(activityId, stage.id, pickOf(status)).catch(failed);
      replace(item);
    },
    [entries, replace, failed],
  );
  /** `통과로 표시`: every suggested stage takes its schedule's 통과 status, one by one; a failed one is left as it was. */
  async function acceptAutopass() {
    if (!suggestion) return;
    const { activity, stages } = suggestion;
    setSuggestion(undefined);
    let failure = false;
    for (const stage of stages) {
      const passed = passedOption(stage);
      if (!passed) continue;
      track("stage_mark", { ...activityParams(activity), kind: "passed", via: "autopass_prompt" });
      try {
        replace(await markStage(activity.id, stage.id, pickOf(passed)));
      } catch (error) {
        if (error instanceof RequestError && error.status === 401) return expired();
        failure = true;
      }
    }
    if (failure) setNotice({ id: Date.now(), text: "일부 단계를 통과로 표시하지 못했어요. 내 활동에서 다시 표시해 주세요." });
  }

  const value = useMemo<Mine>(
    () => ({
      status,
      entries,
      favorites,
      entry: (activityId) => entries.find((entry) => entry.item.activityId === activityId),
      add,
      remove,
      mark,
      reload: () => setAttempt((value) => value + 1),
    }),
    [status, entries, favorites, add, remove, mark],
  );
  return (
    <MineContext value={value}>
      {children}
      {/* 지난 선발 단계 통과 제안 (contract): the iOS alert layout; closing by the overlay is `그대로 두기`. */}
      <Dialog open={!!suggestion} onOpenChange={(open) => !open && setSuggestion(undefined)}>
        <DialogContent>
          <DialogTitle>지난 선발 단계를 통과로 표시할까요?</DialogTitle>
          <DialogDescription>{suggestion && autopassMessage(suggestion.stages, suggestion.remaining)}</DialogDescription>
          <DialogActions>
            <button type="button" className="text-body text-teal" onClick={() => setSuggestion(undefined)}>
              그대로 두기
            </button>
            <button type="button" className="text-body font-semibold text-teal" onClick={acceptAutopass}>
              통과로 표시
            </button>
          </DialogActions>
        </DialogContent>
      </Dialog>
      <Toast notice={notice} />
    </MineContext>
  );
}

export function useMine(): Mine {
  const value = useContext(MineContext);
  if (!value) throw new Error("useMine needs MineProvider");
  return value;
}
