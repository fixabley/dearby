"use client";
import { InstallOffer } from "./install-offer";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { Card, SharePage as Share } from "@/lib/models";
import {
  errorMessage,
  mutateGuest,
  request,
  RequestError,
  saveFailure,
} from "@/lib/client";
import { inAppBrowser } from "@/lib/in-app-browser";
import { Failure, Loading } from "@/shared/ui/states";
import { ExternalBrowserNotice } from "@/shared/ui/notice";
import { MissingCard } from "@/widgets/card/missing-card";
import {
  SharedCardView,
  type SharedCardSaveState,
} from "@/widgets/card/shared-card";

const noSubscription = () => () => {};

/** `/s/:shareId`: public card plus the activities its sender chose, saved in one step. */
export function SharePage({ id }: { id: string }) {
  const [share, setShare] = useState<Share>();
  const [error, setError] = useState("");
  const [missing, setMissing] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [saveState, setSaveState] = useState<SharedCardSaveState>("idle");
  const [saveError, setSaveError] = useState("");
  const container = useRef<HTMLDivElement>(null);
  const inApp = useSyncExternalStore(
    noSubscription,
    () => inAppBrowser(navigator.userAgent),
    () => undefined,
  );
  useEffect(() => {
    const controller = new AbortController();
    request<Share>(`/shares/${id}`, { signal: controller.signal })
      .then(setShare)
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RequestError && error.status === 404)
          setMissing(true);
        else setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [id, attempt]);
  useEffect(() => {
    // The save button disappears; keep keyboard and screen-reader focus on the result.
    if (saveState === "saved")
      container.current
        ?.querySelector<HTMLElement>('a[href="/saved"]')
        ?.focus();
  }, [saveState]);
  async function save() {
    setSaveState("saving");
    setSaveError("");
    try {
      await mutateGuest(`/guest/shares/${id}`, "PUT");
      // A successful write alone is not enough if the browser rejected the cookie.
      const saved = await request<{ items: Card[] }>("/guest/cards");
      if (!saved.items.some((item) => item.id === share!.card.id))
        throw new Error("COOKIE_UNAVAILABLE");
      setSaveState("saved");
    } catch (error) {
      const failure = saveFailure(error);
      if (failure === "missing") setMissing(true);
      else {
        setSaveError(failure);
        setSaveState("error");
      }
    }
  }
  if (missing) return <MissingCard />;
  if (error)
    return (
      <Failure
        message={error}
        retry={() => {
          setError("");
          setAttempt((value) => value + 1);
        }}
      />
    );
  if (!share) return <Loading />;
  return (
    <div ref={container}>
      {inApp && (
        <ExternalBrowserNotice
          appName={inApp}
          onCopyLink={() => navigator.clipboard?.writeText(location.href)}
        />
      )}
      <SharedCardView
        card={share.card}
        activities={share.share.activities}
        saveState={saveState}
        errorMessage={saveError}
        onSave={save}
      />
      {saveState === "saved" && <InstallOffer />}
    </div>
  );
}
