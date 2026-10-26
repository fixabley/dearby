"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { useMine, activityParams } from "@/features/mine/model/mine-context";
import { buildProgress, pickTodayEvent, todayDetail } from "@/features/mine/model/stages";
import { track } from "@/shared/lib/analytics";
import { Badge } from "@/shared/ui/badge";
import { Card } from "@/shared/ui/card";
import { Icon } from "@/shared/ui/icon";
import { TimetableSheet, type TimetableTarget } from "./timetable";

/**
 * 오늘 내 행사 (contract "오늘 내 행사 카드" 2026-10-26) at the top of 발견: the one applied activity whose 활동 일정 block is today or within 7
 * days, from what the account already holds (no extra request). `오늘`/`D-n` badge, the activity name, `외 n개`, and the next favorited
 * session or block. Today with a timetable opens the block's 시간표 modal (the web has no 찜한 세션 tab, so the whole table); otherwise
 * the progress page. Today also offers `명함 QR` → `/cards/mine?qr=<activityId>` (the QR screen with this activity preselected,
 * contract "웹 화면 — 2단계 C"). Nothing renders signed out, while loading, or without a qualifying activity.
 */
export function TodayCard({ className }: { className?: string }) {
  const mine = useMine();
  const router = useRouter();
  const [now, setNow] = useState(Date.now);
  const [timetableOpen, setTimetableOpen] = useState(false);
  // The sheet may switch to a day view (#447); it starts on the card's block each time it opens.
  const [target, setTarget] = useState<TimetableTarget>();
  // Redrawn each minute so `지금` and the next session move on.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);
  const event = useMemo(() => {
    if (mine.status !== "ready") return;
    const applied = mine.entries.flatMap((entry) =>
      entry.activity ? [{ activity: entry.activity, progress: buildProgress(entry.activity, entry.item, now) }] : [],
    );
    return pickTodayEvent(applied, mine.favorites, now);
  }, [mine.status, mine.entries, mine.favorites, now]);
  if (!event) return null;
  const today = event.days === 0;
  const badge = today ? "오늘" : `D-${event.days}`;
  const timetable = today && !!event.block.hasTimetable;
  const open = () => {
    track("today_card_tap", { ...activityParams(event.activity), target: timetable ? "timetable" : "progress" });
    if (timetable) {
      setTarget({ kind: "block", schedule: event.block });
      setTimetableOpen(true);
    } else router.push(`/mine/${event.activity.id}`);
  };
  return (
    <Card className={className}>
      <button
        type="button"
        onClick={open}
        aria-haspopup={timetable ? "dialog" : undefined}
        aria-label={[`${badge} · ${event.activity.name}`, event.others > 0 && `외 ${event.others}개`, todayDetail(event, now)].filter(Boolean).join(", ")}
        className="flex w-full flex-col gap-1.5 p-4 text-left active:opacity-70"
      >
        <span className="flex items-baseline gap-2">
          <Badge variant="filled">{badge}</Badge>
          <span className="min-w-0 flex-1 text-headline font-semibold break-keep">{event.activity.name}</span>
          {event.others > 0 && <span className="shrink-0 text-footnote text-secondary-label">외 {event.others}개</span>}
        </span>
        <span className="flex items-center gap-1.5">
          <span className={today ? "flex-1 text-subheadline" : "flex-1 text-subheadline text-secondary-label"}>{todayDetail(event, now)}</span>
          <Icon name="chevron-right" size="inline" tone="secondary" />
        </span>
      </button>
      {today && (
        <Link
          href={`/cards/mine?qr=${event.activity.id}`}
          onClick={() => track("today_card_tap", { ...activityParams(event.activity), target: "qr" })}
          className="flex min-h-11 items-center justify-center gap-1.5 border-t border-separator text-subheadline font-semibold text-teal active:opacity-70"
        >
          <Icon name="qr" size="inline" />
          명함 QR
        </Link>
      )}
      {timetable && (
        <TimetableSheet
          activityId={event.activity.id}
          activityName={event.activity.name}
          target={target}
          open={timetableOpen}
          onOpenChange={setTimetableOpen}
          onTargetChange={setTarget}
        />
      )}
    </Card>
  );
}
