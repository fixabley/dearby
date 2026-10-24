import { useSyncExternalStore } from "react";
import { z } from "zod";
import { type Recommendation, fieldTagsOf } from "./models";

/**
 * 맞춤 대기열 reactions (contract "맞춤 대기열(스토리형 쇼츠)" › 신호): the web has no sign-in, so they stay in this browser's
 * localStorage and are never sent. Only what changes this device's queue is kept — 관심 있어요 and the `⋯` sheet choices; viewing
 * signals (끝까지 봄·넘김·자세히) have no local use.
 */
const MAX = 200;
const ids = z.array(z.number().int().positive()).max(MAX).catch([]);
const signalsSchema = z.object({
  liked: ids,
  dismissed: ids,
  ineligible: ids,
  hiddenPrograms: ids,
  lessFields: ids,
});
export type QueueSignals = z.infer<typeof signalsSchema>;
export const noSignals: QueueSignals = { liked: [], dismissed: [], ineligible: [], hiddenPrograms: [], lessFields: [] };

const key = "dearby.discovery-queue-signals";
const changed = "dearby:discovery-queue-signals";
// Kept for this page's life when storage is blocked, so a choice still applies until reload.
let memory: string | null = null;
let cached: { raw: string | null; value: QueueSignals } | undefined;

function read(): QueueSignals {
  let raw = memory;
  try {
    raw = localStorage.getItem(key) ?? memory;
  } catch {}
  if (cached?.raw !== raw) {
    let value = noSignals;
    try {
      if (raw !== null) value = signalsSchema.parse(JSON.parse(raw));
    } catch {}
    cached = { raw, value };
  }
  return cached.value;
}
function subscribe(onChange: () => void) {
  window.addEventListener("storage", onChange);
  window.addEventListener(changed, onChange);
  return () => {
    window.removeEventListener("storage", onChange);
    window.removeEventListener(changed, onChange);
  };
}

/** This browser's reactions; `undefined` while server rendering. */
export function useQueueSignals(): QueueSignals | undefined {
  return useSyncExternalStore(subscribe, read, () => undefined);
}

export function saveSignals(signals: QueueSignals) {
  memory = JSON.stringify(signals);
  try {
    localStorage.setItem(key, memory);
  } catch {}
  window.dispatchEvent(new Event(changed));
}

/** `list` with `id` added (newest last, oldest dropped past the cap) or removed. */
export function toggled(list: number[], id: number, on: boolean): number[] {
  const rest = list.filter((value) => value !== id);
  return on ? [...rest, id].slice(-MAX) : rest;
}

/** Still shown: not 관심 없어요, not 지원 자격이 안 돼요, and its program not hidden. */
export function isShown(item: Recommendation, signals: QueueSignals): boolean {
  return (
    !signals.dismissed.includes(item.activity.id) &&
    !signals.ineligible.includes(item.activity.id) &&
    !signals.hiddenPrograms.includes(item.program.id)
  );
}

export function fieldTagsOfItem(item: Recommendation) {
  return item.activity.fieldTagsAll ?? fieldTagsOf(item.activity);
}

export const QUEUE_SIZE = 10;

/**
 * The next round's activity ids: the server's order without shown-before or excluded ones, and `이 분야는 덜 보기` fields moved
 * behind the rest (덜, not 안).
 */
export function nextRound(items: Recommendation[], signals: QueueSignals, seen: number[]): number[] {
  const pool = items.filter((item) => isShown(item, signals) && !seen.includes(item.activity.id));
  const less = (item: Recommendation) => fieldTagsOfItem(item).some((tag) => signals.lessFields.includes(tag.id));
  return [...pool.filter((item) => !less(item)), ...pool.filter(less)]
    .slice(0, QUEUE_SIZE)
    .map((item) => item.activity.id);
}

/**
 * Where the viewer is, kept for this tab (sessionStorage) so `자세히` and back resume the same card instead of a new round.
 */
const positionSchema = z.object({
  round: z.array(z.number().int()).max(QUEUE_SIZE),
  seen: z.array(z.number().int()).max(1000),
  index: z.number().int().nonnegative(),
});
export type QueuePosition = z.infer<typeof positionSchema>;
const positionKey = "dearby.discovery-queue-position";

export function readPosition(): QueuePosition | undefined {
  try {
    return positionSchema.parse(JSON.parse(sessionStorage.getItem(positionKey) ?? ""));
  } catch {}
}
export function savePosition(position: QueuePosition) {
  try {
    sessionStorage.setItem(positionKey, JSON.stringify(position));
  } catch {}
}
