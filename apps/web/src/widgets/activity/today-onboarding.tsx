"use client";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { type Activity, type ActivityPage, type Schedule, type ScheduleDay, clockTime, foldScheduleDays, parseTime, programLogoSrc, seoulDay } from "@/lib/models";
import { publicRead } from "@/lib/client";
import { useMine } from "@/features/mine/model/mine-context";
import { todayBlocks } from "@/features/mine/model/stages";
import { track } from "@/shared/lib/analytics";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { CheckRow } from "@/shared/ui/check-row";
import { Dialog, DialogActions, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { Icon } from "@/shared/ui/icon";
import { ProgramAvatar } from "./program-avatar";
import { TimetableSheet, type TimetableTarget } from "./timetable";

const DISMISSED_KEY = "dearby.todayOnboarding.dismissedDay";
const SHOWN_KEY = "dearby.todayOnboarding.shownDay";
/** A read that found no event today (contract: once per day on a successful read): not asked again that day. */
const NONE_KEY = "dearby.todayOnboarding.noneDay";
/** 대상 읽기 (contract "오늘 행사 온보딩" › 대상): events of any open-or-running state, first page only, without signing in. */
const CANDIDATES = "/activities?category=CONFERENCE,MEETUP,LECTURE,WORKSHOP&status=IN_PROGRESS,RECRUITMENT_CLOSED,NO_RECRUITMENT&size=50";
const SEOUL = "Asia/Seoul";

function stored(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function store(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Without storage the banner may come back on the next visit today; nothing else depends on it.
  }
}

type Target = { activity: Activity; blocks: Schedule[] };

/** `10:00–17:30`: today's timed blocks from the earliest start to the latest end, in the block's zone; `종일` without a timed block. */
function todayRange(blocks: Schedule[]): string {
  const timed = blocks.filter((block) => parseTime(block.startAt) !== undefined);
  if (!timed.length) return "종일";
  const zone = timed[0].timeZone ?? SEOUL;
  const start = Math.min(...timed.map((block) => parseTime(block.startAt)!));
  const ends = timed.flatMap((block) => parseTime(block.endAt) ?? []);
  return `${clockTime(start, zone)}${ends.length ? `–${clockTime(Math.max(...ends), zone)}` : ""}`;
}
/**
 * The 시간표 `참여해요` opens (contract "오늘 행사 온보딩" › 시트): today's blocks folded into one day (하루 시간표) when they are, else the
 * first of today's blocks with a timetable; none when no block today has one (then the progress page opens instead).
 */
function timetableTarget(blocks: Schedule[], now: number): TimetableTarget | undefined {
  const block = blocks.find((each) => each.hasTimetable);
  if (!block) return;
  const day = foldScheduleDays(blocks, now).find((item): item is ScheduleDay => "schedules" in item);
  return day ? { kind: "day", day } : { kind: "block", schedule: block };
}

/**
 * 오늘 행사 온보딩 on the web (contract "오늘 행사 온보딩 — 2026-10-27" › 웹): a one-line banner at the top of 발견, above the 오늘 내 행사
 * card, asking about today's events the account has not added. Only once 내 활동 is read (signed in, consent given), with a target,
 * and not closed today (`dearby.todayOnboarding.dismissedDay`, Seoul `yyyyMMdd`). `고르기` opens the sheet; `참여해요` adds the chosen
 * events, accepts their 통과 제안 without asking and opens the first one's today timetable (`내 시간표` with a favorite there, else `전체`;
 * no timetable → `/mine/:id`). A read with no event today is remembered for the day (`dearby.todayOnboarding.noneDay`).
 * A failed read shows nothing: the banner is optional.
 */
export function TodayOnboarding({ className }: { className?: string }) {
  const mine = useMine();
  const router = useRouter();
  const [now] = useState(Date.now);
  const today = seoulDay(now).replaceAll("-", "");
  const [dismissed, setDismissed] = useState(() => stored(DISMISSED_KEY) === today);
  const [found, setFound] = useState<Activity[]>();
  // The sheet's list is fixed when it opens, so rows do not vanish while `참여해요` adds them.
  const [sheet, setSheet] = useState<Target[]>();
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const [timetable, setTimetable] = useState<{ activity: Activity; target: TimetableTarget; favorited: boolean }>();
  const [timetableOpen, setTimetableOpen] = useState(false);
  const ready = mine.status === "ready";

  useEffect(() => {
    if (!ready || dismissed || stored(NONE_KEY) === today) return;
    const controller = new AbortController();
    publicRead<ActivityPage>(CANDIDATES, { signal: controller.signal }).then(
      (page) => {
        if (!page.items.some((activity) => todayBlocks(activity, now).length)) store(NONE_KEY, today);
        setFound(page.items);
      },
      () => {},
    );
    return () => controller.abort();
  }, [ready, dismissed, today, now]);

  // Today's events not in 내 활동 (by id, so a row still loading its activity counts), first block first, at most five.
  const targets = useMemo(() => {
    if (!ready || !found) return [];
    const added = new Set(mine.entries.map((entry) => entry.item.activityId));
    const midnight = Date.parse(`${seoulDay(now)}T00:00:00+09:00`);
    return found
      .flatMap((activity) => {
        if (added.has(activity.id)) return [];
        const blocks = todayBlocks(activity, now);
        return blocks.length ? [{ activity, blocks, start: Math.min(...blocks.map((block) => parseTime(block.startAt) ?? midnight)) }] : [];
      })
      .sort((a, b) => a.start - b.start || a.activity.id - b.activity.id)
      .slice(0, 5)
      .map(({ activity, blocks }): Target => ({ activity, blocks }));
  }, [ready, found, mine.entries, now]);
  const visible = ready && !dismissed && targets.length > 0;
  const count = targets.length;

  // 사용 통계 `today_onboarding_shown`: once per Seoul day, when the banner first shows.
  useEffect(() => {
    if (!visible || stored(SHOWN_KEY) === today) return;
    store(SHOWN_KEY, today);
    track("today_onboarding_shown", { count });
  }, [visible, count, today]);

  function close() {
    store(DISMISSED_KEY, today);
    setDismissed(true);
    setSheet(undefined);
  }
  /** `오늘은 아니에요`, closing the sheet from outside, or the banner's ×. */
  function skip() {
    close();
    track("today_onboarding_answer", { result: "skip", count: 0 });
  }
  function openSheet() {
    setSheet(targets);
    setPicked(new Set(targets.length === 1 ? [targets[0].activity.id] : []));
    setFailed(false);
  }
  function toggle(id: number, on: boolean) {
    setPicked((current) => {
      const next = new Set(current);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }
  async function join() {
    if (!sheet || busy) return;
    const chosen = sheet.filter((target) => picked.has(target.activity.id));
    setBusy(true);
    setFailed(false);
    const results = await Promise.allSettled(chosen.map((target) => mine.add(target.activity, "today_onboarding")));
    setBusy(false);
    // The ones that were added carry on; only when none was does the sheet stay with an error line.
    const first = chosen.find((_, index) => results[index].status === "fulfilled");
    if (!first) return setFailed(true);
    close();
    track("today_onboarding_answer", { result: "joined", count: chosen.length });
    const target = timetableTarget(first.blocks, Date.now());
    if (!target) return router.push(`/mine/${first.activity.id}`);
    // `내 시간표` only when a session shown there is already favorited; otherwise `전체` (contract › 시트).
    const shown = new Set((target.kind === "day" ? target.day.schedules : [target.schedule]).map((schedule) => schedule.id));
    const favorited = mine.favorites.some((item) => item.activityId === first.activity.id && shown.has(item.scheduleId));
    setTimetable({ activity: first.activity, target, favorited });
    setTimetableOpen(true);
  }

  return (
    <>
      {visible && (
        <Card className={cn("flex items-center gap-1 py-1 pr-1 pl-4", className)}>
          <p className="min-w-0 flex-1 text-subheadline break-keep">오늘 열리는 행사에 참여하나요?</p>
          <Button variant="plain" size="sm" aria-haspopup="dialog" onClick={openSheet}>
            고르기
          </Button>
          <Button variant="plain" size="icon" aria-label="오늘은 닫기" className="text-secondary-label" onClick={skip}>
            <Icon name="close" size="inline" />
          </Button>
        </Card>
      )}
      <Dialog open={!!sheet} onOpenChange={(open) => !open && !busy && skip()}>
        <DialogContent className="w-[min(22rem,calc(100vw-2rem))]">
          <DialogTitle>오늘 참여하는 행사가 있나요?</DialogTitle>
          <DialogDescription className="pb-3">고른 행사를 내 활동에 넣고 오늘 시간표를 열어 드려요.</DialogDescription>
          <div className="max-h-[50dvh] divide-y divide-separator overflow-y-auto border-t border-separator text-left">
            {sheet?.map(({ activity, blocks }) => {
              const logo = programLogoSrc(activity.program.logoUrl);
              const place = activity.place?.name || activity.location;
              return (
                <CheckRow
                  key={activity.id}
                  checked={picked.has(activity.id)}
                  disabled={busy}
                  onChange={(on) => toggle(activity.id, on)}
                >
                  <span className="flex items-center gap-3">
                    <ProgramAvatar key={logo ?? ""} name={activity.program.name} src={logo} />
                    <span className="flex min-w-0 flex-col">
                      <span className="font-semibold">{activity.name}</span>
                      {place && (
                        <span className="text-footnote text-secondary-label">
                          <span className="sr-only">, </span>
                          {place}
                        </span>
                      )}
                      <span className="text-footnote text-secondary-label tabular-nums">
                        <span className="sr-only">, 오늘 </span>
                        {todayRange(blocks)}
                      </span>
                    </span>
                  </span>
                </CheckRow>
              );
            })}
          </div>
          {failed && <p className="border-t border-separator px-4 py-2 text-footnote text-danger">내 활동에 넣지 못했어요. 다시 시도해 주세요.</p>}
          <DialogActions>
            <button type="button" className="text-body text-teal disabled:opacity-50" disabled={busy} onClick={skip}>
              오늘은 아니에요
            </button>
            <button type="button" className="text-body font-semibold text-teal disabled:opacity-50" disabled={busy || !picked.size} onClick={join}>
              참여해요
            </button>
          </DialogActions>
        </DialogContent>
      </Dialog>
      {timetable && (
        <TimetableSheet
          activityId={timetable.activity.id}
          activityName={timetable.activity.name}
          target={timetable.target}
          open={timetableOpen}
          onOpenChange={setTimetableOpen}
          onTargetChange={(target) => setTimetable({ ...timetable, target })}
          initialView={timetable.favorited ? "mine" : "all"}
        />
      )}
    </>
  );
}
