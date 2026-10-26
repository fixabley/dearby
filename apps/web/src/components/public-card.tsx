"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { type Activity, type Card, type GuestItem, sessionContextLines } from "@/lib/models";
import {
  errorMessage,
  mutateGuest,
  request,
  RequestError,
  saveFailure,
} from "@/lib/client";
import { track } from "@/shared/lib/analytics";
import { Failure } from "@/shared/ui/states";
import { SkeletonGroup } from "@/shared/ui/skeleton";
import { Icon } from "@/shared/ui/icon";
import { BottomBar } from "@/shared/ui/bottom-bar";
import { Button } from "@/shared/ui/button";
import { CardCodeRequired, MissingCard } from "@/widgets/card/missing-card";
import { PublicCardBody, PublicCardBodySkeleton } from "@/widgets/card/public-card-body";
import { InstallOffer } from "./install-offer";
import { BlockCard, ReportCard } from "@/widgets/card/report-dialog";
/**
 * `activity`: the numeric activity id from the owner's QR; shown as `<프로그램> <활동>에서 받은 명함` and saved with the card.
 * `code`: the QR code (contract "명함 QR 코드"); kept in memory only and sent with the view and both saves.
 */
export function PublicCard({ id, activity, code }: { id: string; activity?: number; code?: string }) {
  const [card, setCard] = useState<Card>();
  const [place, setPlace] = useState("");
  const [error, setError] = useState("");
  const [missing, setMissing] = useState(false);
  const [codeRequired, setCodeRequired] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  // The server's judgment of where the card was received (교환 세션 맥락), read back from the list the save is verified against.
  const [savedSession, setSavedSession] = useState<string[]>([]);
  const [saveError, setSaveError] = useState("");
  // Saved in this browser already (opened from /saved, or just saved): 삭제하고 차단하기 is offered then.
  const [stored, setStored] = useState(false);
  const router = useRouter();
  // Drop `code` from the address so a copied or shared link does not carry it.
  useEffect(() => {
    const url = new URL(window.location.href);
    if (!url.searchParams.has("code")) return;
    url.searchParams.delete("code");
    history.replaceState(history.state, "", url);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    // The code goes to the proxy as a header, not a query, so it stays out of request logs here.
    request<Card>(`/cards/${id}`, {
      signal: controller.signal,
      headers: code ? { "X-Card-Code": code } : undefined,
    })
      .then((found) => {
        setCard(found);
        track("card_view", activity === undefined ? {} : { activity_id: activity });
        request<{ items: GuestItem[] }>("/guest/cards", { signal: controller.signal })
          .then((list) => setStored(list.items.some((item) => item.card.id.toLowerCase() === id)))
          .catch(() => {});
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RequestError && error.status === 404)
          setMissing(true);
        else if (error instanceof RequestError && error.status === 403)
          setCodeRequired(true);
        else setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [id, code, activity, attempt]);
  // The exchange place is decoration: a missing or deleted activity leaves the line out.
  useEffect(() => {
    if (activity === undefined) return;
    const controller = new AbortController();
    request<Activity>(`/activities/${activity}`, { signal: controller.signal })
      .then((found) => setPlace(`${found.program.name} ${found.name}`))
      .catch(() => {});
    return () => controller.abort();
  }, [activity]);
  const appQuery = new URLSearchParams();
  if (activity !== undefined) appQuery.set("activity", String(activity));
  if (code) appQuery.set("code", code);
  const appLink = `dearby://cards/${id}${appQuery.size ? `?${appQuery}` : ""}`;
  async function save() {
    setBusy(true);
    setSaveError("");
    try {
      await mutateGuest(
        `/guest/cards/${id}`,
        "PUT",
        { ...(activity !== undefined && { activityId: activity }), ...(code && { code }) },
      );
      // A successful write alone is not enough if the browser rejected the cookie.
      const result = await request<{ items: GuestItem[] }>("/guest/cards");
      const item = result.items.find((item) => item.card.id.toLowerCase() === id);
      if (!item) throw new Error("COOKIE_UNAVAILABLE");
      setSavedSession(sessionContextLines(item.context));
      setSaved(true);
      setStored(true);
      track("card_save_browser");
    } catch (error) {
      const failure = saveFailure(error);
      if (error instanceof RequestError && error.status === 403) {
        setCodeRequired(true);
        setCard(undefined);
      } else if (failure === "missing") {
        setMissing(true);
        setCard(undefined);
      } else setSaveError(failure);
    } finally {
      setBusy(false);
    }
  }
  if (missing) return <MissingCard />;
  if (codeRequired) return <CardCodeRequired />;
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
  if (!card)
    return (
      <SkeletonGroup className="mx-auto max-w-[480px] pt-3">
        <PublicCardBodySkeleton />
      </SkeletonGroup>
    );
  return (
    <>
      <article className="mx-auto max-w-[480px] pt-3 pb-64">
        {place && <p className="mb-3 text-center text-footnote text-secondary-label">{place}에서 받은 명함</p>}
        <PublicCardBody card={card} />
        {/* 신고·차단 (contract "명함 신고·차단"): small text buttons under the card. */}
        <div className="mt-2 flex justify-center gap-2">
          <ReportCard id={id} code={code} />
          {stored && <BlockCard id={id} name={card.name} blocked={() => router.push("/saved")} />}
        </div>
        {saved && <InstallOffer place="card" />}
        <p className="mt-3 px-4 text-footnote text-secondary-label">
          이 브라우저의 쿠키로 저장 목록을 구분해요. 브라우저에 저장한 명함은 30일 뒤 사라져요. 쿠키를 삭제하거나 다른 브라우저를
          사용하면 기존 목록에 접근할 수 없어요.
        </p>
      </article>
      {/* 저장 CTA는 하단 고정 (contract "웹 리디자인" › 공개 명함). */}
      <BottomBar>
        {saveError && (
          <p role="alert" className="text-center text-footnote text-danger">
            {saveError}{" "}
            <Link href="/saved" className="underline">
              저장한 명함 보기
            </Link>
          </p>
        )}
        <Button asChild size="lg">
          <a href={appLink} onClick={() => track("card_open_app")}>
            <Icon name="card" size="inline" />
            Dearby 앱에 저장
          </a>
        </Button>
        {saved ? (
          <>
            <p role="status" className="flex flex-col items-center text-footnote">
              <span className="flex items-center gap-1 font-semibold text-teal">
                <Icon name="check-circle" size="inline" />
                명함을 저장했어요.
              </span>
              {savedSession.length > 0 && (
                <span className="text-center break-keep text-secondary-label">{savedSession.join(" · ")}</span>
              )}
            </p>
            {/* A full page load, not client navigation: iOS then reads the manifest with this session's one-time code
                (홈 화면 세션 잇기) when the list is added to the home screen. */}
            <Button asChild size="lg" variant="tinted">
              <a href="/saved">저장한 명함 보기</a>
            </Button>
          </>
        ) : (
          <>
            <Button size="lg" variant="tinted" disabled={busy} onClick={save}>
              <Icon name="inbox" size="inline" />
              {busy ? "저장 확인 중…" : "브라우저에 저장"}
            </Button>
            <p className="text-caption text-secondary-label">앱이 없으면 브라우저에 저장할 수 있어요.</p>
          </>
        )}
      </BottomBar>
    </>
  );
}
