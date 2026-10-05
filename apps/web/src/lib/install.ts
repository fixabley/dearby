"use client";
import { useEffect, useState, useSyncExternalStore } from "react";

// Known in-app browser user-agent tokens. Best effort only: an unknown
// in-app browser is simply not detected and saving is never blocked.
const inAppBrowsers: [RegExp, string][] = [
  [/KAKAOTALK/i, "카카오톡"],
  [/NAVER\(inapp/i, "네이버 앱"],
  [/Instagram/, "인스타그램"],
  [/FBAN|FBAV/, "페이스북"],
  [/\bLine\//, "라인"],
  [/DaumApps/, "다음 앱"],
  [/; wv\)/, "앱 내장 브라우저"],
];

export function inAppBrowser(userAgent: string): string | undefined {
  return inAppBrowsers.find(([pattern]) => pattern.test(userAgent))?.[1];
}

/** iPhone/iPad Safari, where installing means Share → Add to Home Screen. */
export function isIosSafari(userAgent: string, maxTouchPoints = 0): boolean {
  const ios =
    /iPhone|iPad|iPod/.test(userAgent) ||
    (/Macintosh/.test(userAgent) && maxTouchPoints > 1);
  return (
    ios &&
    /Safari\//.test(userAgent) &&
    !/CriOS|FxiOS|EdgiOS/.test(userAgent) &&
    !inAppBrowser(userAgent)
  );
}

type InstallPromptEvent = Event & { prompt: () => Promise<void> };

const standaloneQuery = "(display-mode: standalone)";
function watchStandalone(change: () => void) {
  const query = matchMedia(standaloneQuery);
  query.addEventListener("change", change);
  return () => query.removeEventListener("change", change);
}
const isStandalone = () =>
  matchMedia(standaloneQuery).matches ||
  (navigator as Navigator & { standalone?: boolean }).standalone === true;

/**
 * Install state for the home-screen web app. `prompt` exists only after
 * Chrome fires beforeinstallprompt; `installed` hides install UI.
 */
export function useInstall() {
  const standalone = useSyncExternalStore(
    watchStandalone,
    isStandalone,
    () => false,
  );
  const [event, setEvent] = useState<InstallPromptEvent>();
  const [appInstalled, setAppInstalled] = useState(false);
  useEffect(() => {
    const ready = (value: Event) => {
      value.preventDefault();
      setEvent(value as InstallPromptEvent);
    };
    const done = () => {
      setAppInstalled(true);
      setEvent(undefined);
    };
    addEventListener("beforeinstallprompt", ready);
    addEventListener("appinstalled", done);
    return () => {
      removeEventListener("beforeinstallprompt", ready);
      removeEventListener("appinstalled", done);
    };
  }, []);
  const installed = standalone || appInstalled;
  return {
    installed,
    prompt: event && !installed ? () => event.prompt() : undefined,
  };
}
