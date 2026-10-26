"use client";
import { useSyncExternalStore } from "react";
import { isIos } from "@/lib/install";

/**
 * 비공개 테스트 초대 링크 (contract native-v1.md "명함 교환" › 받는 사람(웹), 2026-10-26): public URLs baked in at build time, like
 * the sign-in ids; an empty value hides that platform. iOS is one TestFlight link; Android is the Google group that gates the
 * Play closed test (optional) and the Play opt-in page.
 */
export const TESTFLIGHT_URL = process.env.NEXT_PUBLIC_TESTFLIGHT_URL?.trim() || undefined;
export const PLAY_GROUP_URL = process.env.NEXT_PUBLIC_PLAY_GROUP_URL?.trim() || undefined;
export const PLAY_TEST_URL = process.env.NEXT_PUBLIC_PLAY_TEST_URL?.trim() || undefined;

export type TesterPlatform = "ios" | "android" | "desktop";
export type TesterTarget = "testflight" | "play_group" | "play_test";

export const iosOffered = TESTFLIGHT_URL !== undefined;
/** The Play opt-in link alone is enough to join; the group step is listed only when a group gates the test. */
export const androidOffered = PLAY_TEST_URL !== undefined;

/** What this browser can install: iOS gets TestFlight, Android the Play steps, anything else both. */
export function offered(platform: TesterPlatform): boolean {
  if (platform === "ios") return iosOffered;
  if (platform === "android") return androidOffered;
  return iosOffered || androidOffered;
}

export function platformOf(userAgent: string, maxTouchPoints: number): TesterPlatform {
  if (isIos(userAgent, maxTouchPoints)) return "ios";
  return /Android/.test(userAgent) ? "android" : "desktop";
}

const noChanges = () => () => {};
/** The platform read from the browser after hydration; `undefined` on the server, so nothing renders until then. */
export function useTesterPlatform(): TesterPlatform | undefined {
  return useSyncExternalStore(noChanges, () => platformOf(navigator.userAgent, navigator.maxTouchPoints), () => undefined);
}
