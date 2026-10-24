import { useSyncExternalStore } from "react";
import { z } from "zod";
import { categoryLabels } from "./category";

/**
 * 추천 설정 (contract "추천 설정 저장"): what the funnel picked. The web has no sign-in, so it lives only in this browser's
 * localStorage; `completedAt` is set even when every question was skipped, so the funnel does not come back.
 */
const preferencesSchema = z.object({
  fieldTagIds: z.array(z.number().int().positive()).max(50),
  categories: z.array(z.string()).max(10),
  traitTagIds: z.array(z.number().int().positive()).max(50),
  completedAt: z.string().nullable(),
});
export type RecommendationPreferences = z.infer<typeof preferencesSchema>;

/** 찾는 활동 종류 choices (contract "추천 설정 퍼널"), as 활동 종류 codes; 멘토십 has no code and is left out. */
export const funnelCategories: [code: string, label: string][] = [
  ["BOOTCAMP", "부트캠프"],
  ["CLUB", "연합동아리"],
  ["CONTEST", "대회"],
  ["HACKATHON", "해커톤"],
  ["CONFERENCE", "컨퍼런스"],
  ["STUDY", "스터디"],
];

const key = "dearby.recommendation-preferences";
const changed = "dearby:recommendation-preferences";
// Kept for this page's life when storage is blocked, so a finished funnel still closes.
let memory: string | null = null;
let cached: { raw: string | null; value: RecommendationPreferences | null } | undefined;

function read(): RecommendationPreferences | null {
  let raw = memory;
  try {
    raw = localStorage.getItem(key) ?? memory;
  } catch {}
  if (cached?.raw !== raw) {
    let value: RecommendationPreferences | null = null;
    try {
      value = raw === null ? null : preferencesSchema.parse(JSON.parse(raw));
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

/** Saved preferences; `null` when the funnel was never finished, `undefined` while server rendering (not known yet). */
export function useRecommendationPreferences(): RecommendationPreferences | null | undefined {
  return useSyncExternalStore(subscribe, read, () => undefined);
}

export function savePreferences(preferences: RecommendationPreferences) {
  memory = JSON.stringify(preferences);
  try {
    localStorage.setItem(key, memory);
  } catch {}
  window.dispatchEvent(new Event(changed));
}

/** Nothing picked: the funnel was skipped, so discovery offers `나에게 맞는 활동 찾기` instead of the 추천 section. */
export function isEmpty(preferences: RecommendationPreferences): boolean {
  return !preferences.fieldTagIds.length && !preferences.categories.length && !preferences.traitTagIds.length;
}

/** `GET /discovery/recommendations` for a browser without an account: the picks travel as query lists (contract "추천 API"). */
export function recommendationsPath(preferences: RecommendationPreferences, size: number): string {
  const query = new URLSearchParams({ size: String(size) });
  if (preferences.fieldTagIds.length) query.set("fieldTagIds", preferences.fieldTagIds.join(","));
  // Only known 활동 종류 codes; the proxy rejects any other.
  const categories = preferences.categories.filter((code) => Object.hasOwn(categoryLabels, code));
  if (categories.length) query.set("categories", categories.join(","));
  if (preferences.traitTagIds.length) query.set("traitTagIds", preferences.traitTagIds.join(","));
  return `/discovery/recommendations?${query}`;
}
