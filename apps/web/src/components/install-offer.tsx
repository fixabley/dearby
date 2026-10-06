"use client";
import { useState, useSyncExternalStore } from "react";
import { isIosSafari, useInstall } from "@/lib/install";
import { InstallPrompt } from "@/shared/ui/install-prompt";

const dismissedKey = "dearby.install-offer.dismissed";
const noChanges = () => () => {};
function storedDismissal() {
  try {
    return localStorage.getItem(dismissedKey) === "1";
  } catch {
    return false;
  }
}

/**
 * Optional home-screen offer shown after cards are saved: Chrome's install button when it offers one,
 * otherwise the Share → Add to Home Screen steps on iOS Safari. Hidden once installed or dismissed.
 * Server rendering shows nothing, so the offer never flashes before the browser checks.
 */
export function InstallOffer() {
  const { installed, prompt } = useInstall();
  const ios = useSyncExternalStore(
    noChanges,
    () => isIosSafari(navigator.userAgent, navigator.maxTouchPoints),
    () => false,
  );
  const stored = useSyncExternalStore(noChanges, storedDismissal, () => true);
  const [dismissedNow, setDismissedNow] = useState(false);
  if (installed || stored || dismissedNow || (!prompt && !ios)) return null;
  return (
    <InstallPrompt
      platform={prompt ? "android" : "ios"}
      onInstall={prompt}
      onDismiss={() => {
        setDismissedNow(true);
        try {
          localStorage.setItem(dismissedKey, "1");
        } catch {}
      }}
    />
  );
}
