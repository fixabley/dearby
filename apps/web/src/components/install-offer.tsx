"use client";
import { useEffect, useState, useSyncExternalStore } from "react";
import { shortcutKind, useInstall } from "@/lib/install";
import { track } from "@/shared/lib/analytics";
import { InstallPrompt } from "@/shared/ui/install-prompt";
import { Card } from "@/shared/ui/card";
import { Button } from "@/shared/ui/button";
import { Icon } from "@/shared/ui/icon";

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
 * Optional 홈 화면 바로가기 offer for the saved list (contract "웹 비로그인 명함 저장"): the browser's install button when it
 * offers one, otherwise its menu steps. `saved` is the bottom sheet on `/saved`; `card` is a small card under a card just saved
 * to the browser, which only offers the install button or points to 저장한 명함 보기 — adding from `/saved` keeps the
 * shortcut on the list, and iOS reads the manifest's one-time session code (홈 화면 세션 잇기) only on a page loaded after saving.
 * Hidden when opened from the home screen, installed, closed or dismissed. Server rendering shows nothing, so it never flashes.
 */
export function InstallOffer({ place }: { place: "saved" | "card" }) {
  const { installed, prompt } = useInstall();
  const kind = useSyncExternalStore(
    noChanges,
    () => shortcutKind(navigator.userAgent, navigator.maxTouchPoints),
    () => undefined,
  );
  const stored = useSyncExternalStore(noChanges, storedDismissal, () => true);
  const [dismissedNow, setDismissedNow] = useState(false);
  // Closed by ×, tapping outside or Escape: hidden until the next visit, not remembered.
  const [closed, setClosed] = useState(false);
  const hidden = installed || stored || dismissedNow || closed;
  // What the offer shows, for analytics: the install button, or the menu steps of this browser.
  const shown = hidden ? undefined : prompt ? "prompt" : kind;
  useEffect(() => {
    if (shown) track("home_shortcut_prompt_shown", { place, kind: shown });
  }, [place, shown]);
  if (!shown) return null;
  const install = prompt && (async () => track("home_shortcut_install", { outcome: await prompt() }));
  const dismiss = () => {
    setDismissedNow(true);
    try {
      localStorage.setItem(dismissedKey, "1");
    } catch {}
  };
  if (place === "saved")
    return <InstallPrompt steps={kind} onInstall={install} onClose={() => setClosed(true)} onDismiss={dismiss} />;
  return (
    <Card role="region" aria-labelledby="home-shortcut-title" className="mx-4 mt-4 p-4">
      <div className="flex items-start gap-2">
        <div className="flex flex-1 flex-col gap-1">
          <h2 id="home-shortcut-title" className="text-headline">
            홈 화면에서 바로 열기
          </h2>
          <p className="text-footnote text-secondary-label">
            {install
              ? "저장한 명함 목록을 홈 화면에 추가해 두면 다음부터 바로 열 수 있어요."
              : "‘저장한 명함 보기’에서 목록을 홈 화면에 추가해 두면 다음부터 바로 열 수 있어요."}
          </p>
        </div>
        <button
          type="button"
          aria-label="안내 닫기"
          className="-m-3 flex size-11 shrink-0 items-center justify-center text-secondary-label"
          onClick={() => setClosed(true)}
        >
          <Icon name="close" size="inline" />
        </button>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {install && (
          <Button size="sm" onClick={install}>
            홈 화면에 추가
          </Button>
        )}
        <Button size="sm" variant="plain" onClick={dismiss}>
          다시 보지 않기
        </Button>
      </div>
    </Card>
  );
}
