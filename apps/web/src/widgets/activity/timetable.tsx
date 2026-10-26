"use client";
import { useEffect, useState } from "react";
import { type Schedule, type Session, type Timetable, clockTime, scheduleLabel } from "@/lib/models";
import { errorMessage, RequestError } from "@/lib/client";
import { publicRead } from "@/features/auth/api/auth-client";
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
import { isBreak, SessionCard, TrackBadge } from "./session-card";

type Row = { start: number; end: number };
/** `전체` is the table; `mine` the favorited sessions as 나만의 시간표 (contract "트랙·세션 시간표" › `찜한 세션`, labelled `내 시간표`). */
type View = "all" | "mine";

/**
 * Rows of the table (contract "트랙·세션 시간표" › 행 나누기): every session boundary splits the block, so tracks with different
 * boundaries still line up; an interval no session covers (a gap) is dropped.
 */
function rowsOf(sessions: Session[]): Row[] {
  const times = [...new Set(sessions.flatMap((s) => [Date.parse(s.startAt), Date.parse(s.endAt)]))].sort((a, b) => a - b);
  return times
    .slice(1)
    .map((end, index) => ({ start: times[index], end }))
    .filter((row) => sessions.some((s) => Date.parse(s.startAt) <= row.start && row.end <= Date.parse(s.endAt)));
}

/**
 * The timetable of one schedule block as a modal (contract "트랙·세션 시간표" › 앱·웹 화면, 2026-10-26 "웹도 마찬가지로"; hearts and the
 * `전체 | 내 시간표` switch since "웹 화면 — 2단계 A"): a near-full-height bottom sheet on phones, a large centred dialog from `md`.
 * Radix gives ESC, the focus trap, focus return and `aria-labelledby`/`aria-describedby` from the title and time range. Content
 * mounts on open, so each open loads afresh and starts on `전체`. Signed out, a footer line still points to the app.
 */
export function TimetableSheet({
  activityId,
  activityName,
  schedule,
  open,
  onOpenChange,
}: {
  activityId: number;
  activityName: string;
  schedule?: Schedule;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [view, setView] = useState<View>("all");
  const { signedIn } = useAuth();
  return (
    <Sheet
      open={open && !!schedule}
      onOpenChange={(next) => {
        if (!next) setView("all");
        onOpenChange(next);
      }}
    >
      <SheetContent className="flex h-[92dvh] flex-col bg-background px-0 md:inset-auto md:top-1/2 md:left-1/2 md:h-[min(85dvh,960px)] md:w-[min(1200px,calc(100vw-4rem))] md:max-w-none md:-translate-x-1/2 md:-translate-y-1/2 md:rounded-card md:pt-5 md:[&>:first-child]:hidden">
        {schedule && (
          <>
            <header className="flex items-start gap-3 px-5 pb-3">
              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                <SheetTitle className="break-keep">
                  {activityName} · {schedule.title || "활동"}
                </SheetTitle>
                <SheetDescription className="tabular-nums">{scheduleLabel(schedule, "activity")}</SheetDescription>
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
              <TimetableView activityId={activityId} schedule={schedule} view={view} />
            </div>
            {!signedIn && <p className="border-t border-separator px-5 pt-3 text-footnote text-secondary-label">내 시간표는 Dearby 앱에서 만들 수 있어요.</p>}
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

/**
 * The timetable of one schedule block, loaded on mount: rows are time slots, columns the tracks in `position` order, an empty slot
 * stays empty and a full-width session (`trackId` null) spans the row. The sheet body scrolls the whole table both ways at once,
 * with the time column and the track row pinned; a wide dialog fits every column without sideways scrolling. Hearts start from the
 * response's `favorited` and the set of favorited ids follows each toggle, so `내 시간표` reflects the taps made here.
 */
function TimetableView({ activityId, schedule, view }: { activityId: number; schedule: Schedule; view: View }) {
  const [timetable, setTimetable] = useState<Timetable | null>();
  const [favorites, setFavorites] = useState<Set<number>>(new Set());
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const { epoch } = useAuth();
  useEffect(() => {
    const controller = new AbortController();
    publicRead<Timetable>(`/activities/${activityId}/schedules/${schedule.id}/timetable`, { signal: controller.signal })
      .then((found) => {
        setTimetable(found);
        setFavorites(new Set(found.sessions.filter((s) => s.favorited).map((s) => s.id)));
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
  if (timetable === undefined)
    return (
      <SkeletonGroup className="flex flex-col gap-2">
        {[0, 1, 2].map((key) => (
          <Skeleton key={key} className="h-20 rounded-[14px]" />
        ))}
      </SkeletonGroup>
    );
  if (!timetable || !timetable.sessions.length)
    return <p className="px-4 text-subheadline text-secondary-label">시간표가 아직 없어요.</p>;
  const heart = (session: Session) =>
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
  if (view === "mine") return <MyTimetableView timetable={timetable} favorites={favorites} heart={heart} />;
  return <TimetableTable timetable={timetable} heart={heart} />;
}

/** `내 시간표` inside the sheet: a sign-in notice, the empty notice, or the favorited sessions of this block as 나만의 시간표. */
function MyTimetableView({ timetable, favorites, heart }: { timetable: Timetable; favorites: Set<number>; heart: (session: Session) => React.ReactNode }) {
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
  const tracks = new Map(timetable.tracks.map((track) => [track.id, track]));
  const items = timetable.sessions
    .filter((session) => favorites.has(session.id))
    .map((session) => ({ session, track: session.trackId === null ? null : (tracks.get(session.trackId) ?? null) }));
  if (!items.length) return <p className="px-4 py-10 text-center text-subheadline text-secondary-label break-keep">세션에 하트를 누르면 내 시간표에 모여요</p>;
  return (
    <div className="px-1 pb-3">
      <MyTimetableList items={items} timeZone={timetable.schedule.timeZone ?? "Asia/Seoul"} favorite={(item) => heart(item.session)} />
    </div>
  );
}

function TimetableTable({ timetable, heart }: { timetable: Timetable; heart: (session: Session) => React.ReactNode }) {
  const tracks = [...timetable.tracks].sort((a, b) => a.position - b.position);
  const { sessions, schedule } = timetable;
  const timeZone = schedule.timeZone ?? "Asia/Seoul";
  const rows = rowsOf(sessions);
  const columns = Math.max(1, tracks.length);
  const span = (session: Session) =>
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
        {schedule.title} 시간표{tracks.length ? `, 트랙 ${tracks.length}개` : ""}
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
