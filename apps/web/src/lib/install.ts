"use client";
import { useSyncExternalStore } from "react";
import type { ShortcutSteps } from "@/shared/ui/install-prompt";
import { inAppBrowser } from "./in-app-browser";

/**
 * How this browser adds the saved list to the home screen (contract "웹 비로그인 명함 저장" › 홈 화면 바로가기):
 * Share → 홈 화면에 추가 on iOS Safari and Chrome, the ⋮ / ≡ menu on Android Chrome and Samsung Internet. Other browsers and
 * in-app browsers get nothing. iPadOS reports a Mac user agent, hence `maxTouchPoints`.
 */
const isIos = (userAgent: string, maxTouchPoints: number) =>
  /iPhone|iPad|iPod/.test(userAgent) || (/Macintosh/.test(userAgent) && maxTouchPoints > 1);
export function shortcutKind(userAgent: string, maxTouchPoints = 0): ShortcutSteps | undefined {
  if (inAppBrowser(userAgent)) return;
  if (isIos(userAgent, maxTouchPoints)) {
    if (/CriOS/.test(userAgent)) return "ios-chrome";
    return /Safari\//.test(userAgent) && !/FxiOS|EdgiOS|OPiOS/.test(userAgent) ? "ios-safari" : undefined;
  }
  if (!/Android/.test(userAgent)) return;
  if (/SamsungBrowser\//.test(userAgent)) return "samsung";
  return /Chrome\//.test(userAgent) && !/EdgA|OPR|YaBrowser|Firefox/.test(userAgent) ? "chrome" : undefined;
}

type InstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

// Chrome and Samsung Internet fire beforeinstallprompt once, at page load — long before 브라우저에 저장 — so it is caught when
// this module loads, not when the offer mounts.
let deferred: InstallPromptEvent | undefined;
let appInstalled = false;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((listener) => listener());
if (typeof window !== "undefined") {
  addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    deferred = event as InstallPromptEvent;
    emit();
  });
  addEventListener("appinstalled", () => {
    appInstalled = true;
    deferred = undefined;
    emit();
  });
}
const standaloneQuery = "(display-mode: standalone)";
function subscribe(change: () => void) {
  listeners.add(change);
  const query = matchMedia(standaloneQuery);
  query.addEventListener("change", change);
  return () => {
    listeners.delete(change);
    query.removeEventListener("change", change);
  };
}
const isStandalone = () =>
  matchMedia(standaloneQuery).matches || (navigator as Navigator & { standalone?: boolean }).standalone === true;

/**
 * Install state for the home-screen web app. `installed` (opened from the home screen, or installed just now) hides install UI;
 * `prompt` exists only after the browser fired beforeinstallprompt and resolves with the user's choice.
 */
export function useInstall() {
  const installed = useSyncExternalStore(subscribe, () => appInstalled || isStandalone(), () => false);
  const event = useSyncExternalStore(subscribe, () => deferred, () => undefined);
  return {
    installed,
    prompt:
      event && !installed
        ? async () => {
            // An event prompts once; Chrome fires a new one if the user declines.
            deferred = undefined;
            emit();
            await event.prompt();
            return (await event.userChoice).outcome;
          }
        : undefined,
  };
}

/**
 * Opened as the iOS home-screen web app, whose cookie jar is separate from Safari's: an empty saved list there may mean the
 * one-time session code (홈 화면 세션 잇기) expired before the first launch. Android shares the browser's cookies.
 */
export function useIosHomeScreenApp() {
  return useSyncExternalStore(
    subscribe,
    () => isStandalone() && isIos(navigator.userAgent, navigator.maxTouchPoints),
    () => false,
  );
}
