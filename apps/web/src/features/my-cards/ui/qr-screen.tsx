"use client";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { WEB_ORIGIN } from "@/lib/preview";
import type { Activity } from "@/lib/models";
import type { MyCard, QrCode } from "@/lib/account-models";
import { screenView } from "@/shared/lib/analytics";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Icon } from "@/shared/ui/icon";
import { Sheet, SheetContent, SheetTitle } from "@/shared/ui/sheet";
import { Skeleton } from "@/shared/ui/skeleton";
import { activityById, closeQrCode, issueQrCode, myActivities } from "../api/cards-client";
import { qrSvgPath } from "../model/qr-svg";
import { todayActivity } from "../model/today-activity";

/** Refresh this long before the code's `expiresAt` (contract "명함 QR 코드" › 자동 새로고침). */
const REFRESH_BEFORE_MS = 60_000;
/** At most this many of my activities are looked up for the chip. */
const MAX_ACTIVITIES = 20;

/**
 * 내 명함 QR 화면 (contract "명함 교환" › 주인, "명함 QR 코드" › 웹): a full-screen dialog. Opening issues a code (`POST qr-codes`),
 * the QR is `https://<웹 origin>/cards/<id>?activity=<n>&code=<code>`; the code refreshes itself a minute before it expires and on the
 * top-right button (the previous one is closed → 2분 뒤 만료), and closing the screen closes the current code. The activity chip
 * defaults to today's activity among 내 활동 and only changes the address, never the code. Nothing is drawn without a code.
 */
export function QrScreen({ card, onClose, presetActivityId }: { card: MyCard; onClose: () => void; /** 오늘 내 행사 카드의 `명함 QR`이 미리 고른 활동 (contract "오늘 내 행사 카드"). */ presetActivityId?: number }) {
  const [code, setCode] = useState<QrCode>();
  const [issuing, setIssuing] = useState(true);
  const [failed, setFailed] = useState(false);
  const [activities, setActivities] = useState<Activity[]>();
  const [activity, setActivity] = useState<Activity>();
  const [picking, setPicking] = useState(false);
  const current = useRef<QrCode>(undefined);
  useEffect(() => {
    current.current = code;
  }, [code]);

  /** 실패 뒤 다시 시도: 상태만 되돌리면 아래 효과가 다시 발급한다(버튼이 직접 부르면 두 번 발급된다). */
  const retry = () => setFailed(false);
  const issue = useCallback(async () => {
    setIssuing(true);
    setFailed(false);
    const previous = current.current;
    try {
      const next = await issueQrCode(card.id);
      setCode(next);
      if (previous) closeQrCode(card.id, previous.id).catch(() => {});
    } catch {
      setFailed(true);
    } finally {
      setIssuing(false);
    }
  }, [card.id]);
  useEffect(() => {
    screenView("card_qr");
    return () => {
      // 닫기: 지금 코드를 2분 뒤 만료시킨다(실패해도 닫힘은 막지 않는다).
      const open = current.current;
      if (open) closeQrCode(card.id, open.id).catch(() => {});
    };
  }, [card.id]);
  // 처음 열 때 발급하고, 그 뒤로는 expiresAt 1분 전에 자동으로 새로 발급한다. 실패하면 버튼으로만 다시 시도한다.
  useEffect(() => {
    if (failed) return;
    const due = code ? Date.parse(code.expiresAt) - REFRESH_BEFORE_MS - Date.now() : 0;
    const timer = setTimeout(issue, Math.max(due, 0));
    return () => clearTimeout(timer);
  }, [code, failed, issue]);
  // 활동 칩 후보: 내 활동(지워지지 않은 것) → 활동 조회 → 오늘 진행 중인 것 기본.
  useEffect(() => {
    let cancelled = false;
    myActivities()
      .then((items) =>
        Promise.all(
          items
            .filter((item) => !item.deleted)
            .slice(0, MAX_ACTIVITIES)
            .map((item) => activityById(item.activityId).catch(() => undefined)),
        ),
      )
      .then((found) => {
        if (cancelled) return;
        const list = found.filter((item): item is Activity => item !== undefined);
        setActivities(list);
        setActivity(list.find((item) => item.id === presetActivityId) ?? todayActivity(list));
      })
      .catch(() => {
        if (!cancelled) setActivities([]);
      });
    return () => {
      cancelled = true;
    };
  }, [presetActivityId]);

  const url = useMemo(() => {
    if (!code) return;
    const target = new URL(`/cards/${card.id}`, WEB_ORIGIN);
    if (activity) target.searchParams.set("activity", String(activity.id));
    target.searchParams.set("code", code.code);
    return target.toString();
  }, [code, activity, card.id]);
  const qr = useMemo(() => {
    if (!url) return;
    try {
      return qrSvgPath(url);
    } catch {
      return;
    }
  }, [url]);

  return (
    <DialogPrimitive.Root open onOpenChange={(open) => !open && onClose()}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Content
          aria-describedby={undefined}
          className="fixed inset-0 z-50 flex flex-col bg-background pt-[env(safe-area-inset-top)] pb-[max(16px,env(safe-area-inset-bottom))] data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0"
        >
          <div className="mx-auto grid h-11 w-full max-w-[720px] grid-cols-[1fr_auto_1fr] items-center px-4">
            <DialogPrimitive.Close className="-ml-2 inline-flex min-h-11 items-center justify-self-start px-2 text-body text-teal">닫기</DialogPrimitive.Close>
            <DialogPrimitive.Title className="text-headline font-semibold">명함 QR</DialogPrimitive.Title>
            <button
              type="button"
              aria-label="QR 새로 만들기"
              disabled={issuing}
              className="-mr-2 inline-flex size-11 items-center justify-center justify-self-end text-teal disabled:opacity-50"
              onClick={failed ? retry : issue}
            >
              <Icon name="refresh" />
            </button>
          </div>
          <div className="flex flex-1 flex-col items-center justify-center gap-5 px-6">
            <div className="grid size-[min(80vw,320px)] place-items-center rounded-card bg-white p-4 shadow-[0_8px_32px_rgb(0_0_0/0.08)]">
              {qr && !issuing ? (
                <svg
                  role="img"
                  aria-label="내 명함 QR 코드, 카메라로 읽으면 명함을 받아요"
                  viewBox={`0 0 ${qr.size} ${qr.size}`}
                  className="size-full"
                  shapeRendering="crispEdges"
                >
                  <path d={qr.path} fill="#000" />
                </svg>
              ) : failed ? (
                <div role="alert" className="flex flex-col items-center gap-3 text-center">
                  <p className="text-subheadline break-keep text-secondary-label">연결을 확인하고 다시 시도해 주세요</p>
                  <Button variant="tinted" onClick={retry}>
                    다시 시도
                  </Button>
                </div>
              ) : (
                <Skeleton className="size-full rounded-control" aria-label="QR 코드를 만드는 중" />
              )}
            </div>
            <div className="flex flex-col items-center gap-1 text-center">
              <p className="text-headline font-semibold">{card.name}</p>
              {card.profileName && <p className="text-subheadline text-secondary-label">{card.profileName}</p>}
            </div>
            <button
              type="button"
              aria-haspopup="dialog"
              className={cn(
                "inline-flex min-h-11 max-w-full items-center gap-1.5 rounded-full px-4 text-subheadline font-semibold active:opacity-70",
                activity ? "bg-mint text-teal" : "bg-fill text-secondary-label",
              )}
              disabled={activities === undefined}
              onClick={() => setPicking(true)}
            >
              <Icon name="calendar" size="inline" />
              <span className="truncate">{activities === undefined ? "내 활동 확인 중…" : activity ? `${activity.program.name} ${activity.name}` : "활동 없이"}</span>
              <Icon name="chevron-down" size="inline" />
            </button>
            <p className="max-w-[320px] text-center text-footnote break-keep text-secondary-label">
              상대가 카메라로 읽으면 명함을 받아요. 코드는 5분 뒤 만료되고 자동으로 새로 만들어요.
            </p>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
      <Sheet open={picking} onOpenChange={setPicking}>
        <SheetContent aria-describedby={undefined} className="z-[60] max-h-[70dvh] overflow-y-auto">
          <SheetTitle className="pb-2">어느 활동에서 건네나요?</SheetTitle>
          <ul role="radiogroup" aria-label="교환 활동" className="divide-y divide-separator">
            <li>
              <PickRow checked={!activity} label="활동 없이" onPick={() => (setActivity(undefined), setPicking(false))} />
            </li>
            {activities?.map((item) => (
              <li key={item.id}>
                <PickRow
                  checked={activity?.id === item.id}
                  label={item.name}
                  caption={item.program.name}
                  onPick={() => (setActivity(item), setPicking(false))}
                />
              </li>
            ))}
          </ul>
          {activities?.length === 0 && <p className="py-3 text-footnote text-secondary-label">내 활동에 추가한 활동이 없어요. 활동 없이 건넬 수 있어요.</p>}
        </SheetContent>
      </Sheet>
    </DialogPrimitive.Root>
  );
}

function PickRow({ checked, label, caption, onPick }: { checked: boolean; label: string; caption?: string; onPick: () => void }) {
  return (
    <button type="button" role="radio" aria-checked={checked} className="flex min-h-11 w-full items-center gap-3 py-2 text-left active:opacity-70" onClick={onPick}>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-body">{label}</span>
        {caption && <span className="truncate text-footnote text-secondary-label">{caption}</span>}
      </span>
      {checked && <Icon name="check-circle" tone="teal" />}
    </button>
  );
}
