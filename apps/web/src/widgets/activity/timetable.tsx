"use client";
import { useEffect, useState } from "react";
import { type Schedule, type ScheduleDay, type Session, type Timetable, type Track, clockTime, scheduleDayRange, scheduleLabel } from "@/lib/models";
import { errorMessage, publicRead, RequestError } from "@/lib/client";
import { useAuth } from "@/features/auth/model/auth-context";
import { FavoriteHeart } from "@/features/favorites/ui/favorite-heart";
import { track } from "@/shared/lib/analytics";
import { Button } from "@/shared/ui/button";
import { Icon } from "@/shared/ui/icon";
import { Segmented } from "@/shared/ui/segmented";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from "@/shared/ui/sheet";
import { Failure } from "@/shared/ui/states";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { MyTimetableList } from "./my-timetable";
import { type Entry, isBreak, SessionCard, TrackBadge } from "./session-card";

type Row = { start: number; end: number };
/** `전체` is the table; `mine` the favorited sessions as 나만의 시간표 (contract "트랙·세션 시간표" › `내 시간표(찜한 세션)`). */
type View = "all" | "mine";

/**
 * Rows of the table (contract "트랙·세션 시간표" › 행 나누기): every session boundary splits the block, so tracks with different
 * boundaries still line up; an interval no session covers (a gap) is dropped.
 */
function rowsOf(sessions: Entry[]): Row[] {
  const times = [...new Set(sessions.flatMap((s) => [Date.parse(s.startAt), Date.parse(s.endAt)]))].sort((a, b) => a - b);
  return times
    .slice(1)
    .map((end, index) => ({ start: times[index], end }))
    .filter((row) => sessions.some((s) => Date.parse(s.startAt) <= row.start && row.end <= Date.parse(s.endAt)));
}

/**
 * What the timetable sheet shows: one schedule block (with its day when the day holds other timed blocks, for `이 날 전체 보기`) or
 * a whole day (2026-10-26 하루 시간표).
 */
export type TimetableTarget = { kind: "block"; schedule: Schedule; day?: ScheduleDay } | { kind: "day"; day: ScheduleDay };

/** What a loaded block or day shows: its tracks and entries and the zone. */
type Loaded = { caption: string; tracks: Track[]; entries: Entry[]; timeZone: string };

/**
 * The timetable as a modal (contract "트랙·세션 시간표" › 앱·웹 화면, 2026-10-26 "웹도 마찬가지로"; hearts and the `전체 | 내 시간표`
 * switch since "웹 화면 — 2단계 A"): a near-full-height bottom sheet on phones, a large centred dialog from `md`. Radix gives ESC, the
 * focus trap, focus return and `aria-labelledby`/`aria-describedby` from the title and time range. Content mounts on open, so each
 * open loads afresh and starts on `전체`. A block view offers `이 날 전체 보기` when its day has other timed blocks; the day view swaps
 * in inside the same sheet so the dialog is not re-opened. Signed out, the 전체 view carries a one-line sign-in hint.
 */
export function TimetableSheet({
  activityId,
  activityName,
  target,
  open,
  onOpenChange,
  onTargetChange,
}: {
  activityId: number;
  activityName: string;
  target?: TimetableTarget;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onTargetChange: (target: TimetableTarget) => void;
}) {
  const [view, setView] = useState<View>("all");
  const { signedIn } = useAuth();
  return (
    <Sheet
      open={open && !!target}
      onOpenChange={(next) => {
        if (!next) setView("all");
        onOpenChange(next);
      }}
    >
      <SheetContent className="flex h-[92dvh] flex-col bg-background px-0 md:inset-auto md:top-1/2 md:left-1/2 md:h-[min(85dvh,960px)] md:w-[min(1200px,calc(100vw-4rem))] md:max-w-none md:-translate-x-1/2 md:-translate-y-1/2 md:rounded-card md:pt-5 md:[&>:first-child]:hidden">
        {target && (
          <>
            <header className="flex items-start gap-3 px-5 pb-3">
              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                <SheetTitle className="break-keep">
                  {activityName} · {target.kind === "day" ? target.day.label : target.schedule.title || "활동"}
                </SheetTitle>
                <SheetDescription className="tabular-nums">
                  {target.kind === "day" ? scheduleDayRange(target.day) : scheduleLabel(target.schedule, "activity")}
                </SheetDescription>
                {target.kind === "block" && target.day && (
                  <Button
                    variant="plain"
                    size="sm"
                    className="-ml-3 min-h-9 self-start"
                    onClick={() => onTargetChange({ kind: "day", day: target.day! })}
                  >
                    이 날 전체 보기
                    <Icon name="chevron-right" size="inline" />
                  </Button>
                )}
              </div>
              <SheetClose
                aria-label="닫기"
                className="flex size-11 shrink-0 items-center justify-center rounded-full bg-fill text-secondary-label"
              >
                <Icon name="close" size="inline" />
              </SheetClose>
            </header>
            <Segmented
              label="시간표 보기"
              className="mx-5 mb-3 md:max-w-xs"
              options={[
                { value: "all", label: "전체" },
                { value: "mine", label: "내 시간표" },
              ]}
              value={view}
              onChange={setView}
            />
            <div className="min-h-0 flex-1 overflow-auto overscroll-contain px-3">
              {target.kind === "day" ? (
                <DayTimetableView activityId={activityId} day={target.day} view={view} />
              ) : (
                <TimetableView activityId={activityId} schedule={target.schedule} view={view} />
              )}
            </div>
            {/* Signed out, the 전체 view keeps one footer line; the 내 시간표 view already says it with a 로그인 button (2026-10-27 결정). */}
            {!signedIn && view === "all" && (
              <p className="border-t border-separator px-5 pt-3 text-footnote text-secondary-label">로그인하면 웹에서도 내 시간표를 만들 수 있어요.</p>
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

/** Three card-height placeholders while a timetable loads. */
function TimetableLoading() {
  return (
    <SkeletonGroup className="flex flex-col gap-2">
      {[0, 1, 2].map((key) => (
        <Skeleton key={key} className="h-20 rounded-[14px]" />
      ))}
    </SkeletonGroup>
  );
}

/**
 * The timetable of one schedule block, loaded on mount: rows are time slots, columns the tracks in `position` order, an empty slot
 * stays empty and a full-width session (`trackId` null) spans the row. The sheet body scrolls the whole table both ways at once,
 * with the time column and the track row pinned; a wide dialog fits every column without sideways scrolling.
 */
function TimetableView({ activityId, schedule, view }: { activityId: number; schedule: Schedule; view: View }) {
  const [timetable, setTimetable] = useState<Timetable | null>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const { epoch } = useAuth();
  useEffect(() => {
    const controller = new AbortController();
    publicRead<Timetable>(`/activities/${activityId}/schedules/${schedule.id}/timetable`, { signal: controller.signal })
      .then((found) => {
        setTimetable(found);
        track("timetable_view", { activity_id: activityId });
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RequestError && error.status === 404) setTimetable(null);
        else setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [activityId, schedule.id, attempt, epoch]);
  if (error)
    return (
      <Failure
        compact
        message={error}
        retry={() => {
          setError("");
          setAttempt((value) => value + 1);
        }}
      />
    );
  if (timetable === undefined) return <TimetableLoading />;
  if (!timetable || !timetable.sessions.length)
    return <p className="px-4 text-subheadline text-secondary-label">시간표가 아직 없어요.</p>;
  return (
    <LoadedTimetable
      key={epoch}
      activityId={activityId}
      view={view}
      loaded={{
        caption: `${schedule.title} 시간표`,
        tracks: timetable.tracks,
        entries: timetable.sessions,
        timeZone: timetable.schedule.timeZone ?? "Asia/Seoul",
      }}
    />
  );
}

/** One block's timetable result in the day view: loaded, none (404 or no sessions) or failed. */
type BlockResult = Timetable | null | "failed";

/**
 * 하루 시간표 (2026-10-26 사용자 "시간표보기 누르면 여러 트랙 나뉜 시간표처럼 보여주고"): every timed block of the day in one table.
 * The `hasTimetable` blocks load in parallel and are merged — tracks are joined by name across blocks (우아콘's 오전·오후 blocks each
 * carry their own 트랙 A–G rows), sessions keep their times — and a block without a timetable, or one that failed, becomes one
 * full-width band row so the day reads as a whole. A failure in some blocks keeps the rest and offers one retry.
 */
function DayTimetableView({ activityId, day, view }: { activityId: number; day: ScheduleDay; view: View }) {
  const [results, setResults] = useState<Map<number, BlockResult>>();
  const [attempt, setAttempt] = useState(0);
  const { epoch } = useAuth();
  useEffect(() => {
    const controller = new AbortController();
    Promise.all(
      day.schedules
        .filter((schedule) => schedule.hasTimetable)
        .map((schedule) =>
          publicRead<Timetable>(`/activities/${activityId}/schedules/${schedule.id}/timetable`, { signal: controller.signal })
            .then((found): [number, BlockResult] => [schedule.id, found])
            .catch((error): [number, BlockResult] => [schedule.id, error instanceof RequestError && error.status === 404 ? null : "failed"]),
        ),
    ).then((entries) => {
      if (controller.signal.aborted) return;
      setResults(new Map(entries));
      track("timetable_view", { activity_id: activityId });
    });
    return () => controller.abort();
  }, [activityId, day, attempt, epoch]);
  if (results === undefined) return <TimetableLoading />;
  const retry = () => {
    setResults(undefined);
    setAttempt((value) => value + 1);
  };
  const failed = [...results.values()].filter((result) => result === "failed").length;
  if (failed && failed === results.size) return <Failure compact message="시간표를 불러오지 못했어요." retry={retry} />;
  const merged = mergeDay(day, results);
  return (
    <>
      <LoadedTimetable key={epoch} activityId={activityId} view={view} loaded={{ caption: `${day.label} 시간표`, ...merged, timeZone: day.timeZone }} />
      {failed > 0 && (
        <p className="flex items-center justify-between gap-3 px-2 py-2 text-footnote text-secondary-label">
          일부 블록을 불러오지 못했어요.
          <Button variant="plain" size="sm" className="min-h-9" onClick={retry}>
            다시 시도
          </Button>
        </p>
      )}
    </>
  );
}

/**
 * Tracks of the day are the blocks' tracks joined by name, in first-seen `position` order with synthetic ids; each session's
 * `trackId` is remapped. A block with no timetable (none, 404, no sessions) or a failed one is a negative-id full-width entry.
 */
function mergeDay(day: ScheduleDay, results: Map<number, BlockResult>): { tracks: Track[]; entries: Entry[] } {
  const tracks: Track[] = [];
  const entries: Entry[] = [];
  for (const schedule of day.schedules) {
    const result = schedule.hasTimetable ? results.get(schedule.id) : null;
    if (result && result !== "failed" && result.sessions.length) {
      const ids = new Map<number, number>();
      for (const found of [...result.tracks].sort((a, b) => a.position - b.position)) {
        let merged = tracks.find((t) => t.name === found.name);
        if (!merged) {
          merged = { id: tracks.length, name: found.name, room: found.room, position: tracks.length };
          tracks.push(merged);
        }
        ids.set(found.id, merged.id);
      }
      for (const session of result.sessions)
        entries.push({ ...session, trackId: session.trackId === null ? null : (ids.get(session.trackId) ?? null) });
    } else if (schedule.startAt && schedule.endAt) {
      entries.push({
        id: -schedule.id,
        trackId: null,
        kind: "OTHER",
        title: schedule.title || "활동",
        startAt: schedule.startAt,
        endAt: schedule.endAt,
        speakers: [],
        tags: [],
        block: result === "failed" ? "failed" : "plain",
      });
    }
  }
  return { tracks, entries };
}

/**
 * A loaded block or day in the chosen view. Hearts start from the response's `favorited` (keyed by the sign-in epoch, so a reload
 * resets them) and the set of favorited ids follows each toggle, so `내 시간표` reflects the taps made here (contract "트랙·세션 시간표"
 * › 내 시간표, web "웹 화면 — 2단계 A").
 */
function LoadedTimetable({ activityId, loaded, view }: { activityId: number; loaded: Loaded; view: View }) {
  const [favorites, setFavorites] = useState<Set<number>>(() => new Set(loaded.entries.filter((s) => s.favorited).map((s) => s.id)));
  const heart = (session: Entry) =>
    isBreak(session) ? undefined : (
      <FavoriteHeart
        kind="session"
        id={session.id}
        favorited={favorites.has(session.id)}
        activityId={activityId}
        size="sm"
        onChange={(on) =>
          setFavorites((current) => {
            const next = new Set(current);
            if (on) next.add(session.id);
            else next.delete(session.id);
            return next;
          })
        }
      />
    );
  if (view === "mine") return <MyTimetableView loaded={loaded} favorites={favorites} heart={heart} />;
  return <TimetableTable loaded={loaded} heart={heart} />;
}

/** `내 시간표` inside the sheet: a sign-in notice, the empty notice, or the favorited sessions of this block or day as 나만의 시간표. */
function MyTimetableView({ loaded, favorites, heart }: { loaded: Loaded; favorites: Set<number>; heart: (session: Entry) => React.ReactNode }) {
  const { signedIn, openSignIn } = useAuth();
  if (!signedIn)
    return (
      <div className="flex flex-col items-center gap-3 px-4 py-10 text-center">
        <p className="text-subheadline text-secondary-label break-keep">로그인하면 세션에 하트를 눌러 내 시간표를 만들 수 있어요.</p>
        <Button variant="tinted" onClick={openSignIn}>
          로그인
        </Button>
      </div>
    );
  const tracks = new Map(loaded.tracks.map((track) => [track.id, track]));
  const items = loaded.entries
    .filter((session) => !isBreak(session) && favorites.has(session.id))
    .map((session) => ({ session: session as Session, track: session.trackId === null ? null : (tracks.get(session.trackId) ?? null) }));
  if (!items.length) return <p className="px-4 py-10 text-center text-subheadline text-secondary-label break-keep">세션에 하트를 누르면 내 시간표에 모여요</p>;
  return (
    <div className="px-1 pb-3">
      <MyTimetableList items={items} timeZone={loaded.timeZone} favorite={(item) => heart(item.session)} />
    </div>
  );
}

function TimetableTable({ loaded, heart }: { loaded: Loaded; heart: (session: Entry) => React.ReactNode }) {
  const { caption, entries: sessions, timeZone } = loaded;
  const tracks = [...loaded.tracks].sort((a, b) => a.position - b.position);
  const rows = rowsOf(sessions);
  const columns = Math.max(1, tracks.length);
  const span = (session: Entry) =>
    rows.filter((row) => Date.parse(session.startAt) <= row.start && row.end <= Date.parse(session.endAt)).length;
  const at = (row: Row, trackId: number | null) =>
    sessions.find((s) => s.trackId === trackId && Date.parse(s.startAt) <= row.start && row.end <= Date.parse(s.endAt));
  const time = (value: number) => clockTime(value, timeZone);
  return (
    <table
      className="w-full table-fixed border-separate border-spacing-1.5"
      style={{ minWidth: tracks.length > 1 ? `${tracks.length * 11 + 5}rem` : undefined }}
    >
      <caption className="sr-only">
        {caption}
        {tracks.length ? `, 트랙 ${tracks.length}개` : ""}
      </caption>
      <colgroup>
        <col className="w-[4.5rem]" />
        {Array.from({ length: columns }, (_, index) => (
          <col key={index} />
        ))}
      </colgroup>
      {tracks.length > 0 && (
        <thead className="sticky top-0 z-20 bg-background">
          <tr>
            <td className="sticky left-0 z-10 bg-background" />
            {tracks.map((track) => (
              <th key={track.id} scope="col" className="px-1 pb-1 text-left align-bottom">
                <TrackBadge track={track} />
              </th>
            ))}
          </tr>
        </thead>
      )}
      <tbody>
        {rows.map((row) => {
          const wide = at(row, null);
          return (
            <tr key={row.start}>
              <th
                scope="row"
                className="sticky left-0 z-10 bg-background pr-1 text-left align-top text-caption font-semibold text-secondary-label tabular-nums"
              >
                {time(row.start)}
                <span aria-hidden="true"> –</span>
                <span className="sr-only">부터</span>
                <br />
                {time(row.end)}
                <span className="sr-only">까지</span>
              </th>
              {wide
                ? Date.parse(wide.startAt) === row.start && (
                    <td colSpan={columns} rowSpan={span(wide)} className="h-px align-top">
                      <SessionCard session={wide} time={time} spansRows={span(wide) > 1} favorite={heart(wide)} />
                    </td>
                  )
                : tracks.map((track) => {
                    const session = at(row, track.id);
                    if (!session) return <td key={track.id} className="rounded-[14px] bg-fill/40" />;
                    if (Date.parse(session.startAt) !== row.start) return null;
                    return (
                      <td key={track.id} rowSpan={span(session)} className="h-px align-top">
                        <SessionCard session={session} track={track} time={time} spansRows={span(session) > 1} favorite={heart(session)} />
                      </td>
                    );
                  })}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
