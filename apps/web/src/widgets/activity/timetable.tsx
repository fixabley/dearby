"use client";
import { useEffect, useState } from "react";
import { type Schedule, type Session, type Timetable, type Track, clockTime, scheduleLabel } from "@/lib/models";
import { errorMessage, request, RequestError } from "@/lib/client";
import { track } from "@/shared/lib/analytics";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Icon } from "@/shared/ui/icon";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from "@/shared/ui/sheet";
import { Failure } from "@/shared/ui/states";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { TagIcon } from "@/shared/ui/tag-icon";

/** Track colours by `position` (contract "트랙·세션 시간표": the server sends only the order); light and dark pairs keep the badge text readable. */
const TRACK_COLORS = [
  "bg-teal-100 text-teal-900 dark:bg-teal-400/20 dark:text-teal-100",
  "bg-indigo-100 text-indigo-900 dark:bg-indigo-400/20 dark:text-indigo-100",
  "bg-orange-100 text-orange-900 dark:bg-orange-400/20 dark:text-orange-100",
  "bg-pink-100 text-pink-900 dark:bg-pink-400/20 dark:text-pink-100",
  "bg-lime-100 text-lime-900 dark:bg-lime-400/20 dark:text-lime-100",
  "bg-purple-100 text-purple-900 dark:bg-purple-400/20 dark:text-purple-100",
];
const trackColor = (position: number) => TRACK_COLORS[position % TRACK_COLORS.length];

type Row = { start: number; end: number };

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
 * The timetable of one schedule block as a modal (contract "트랙·세션 시간표" › 앱·웹 화면, 2026-10-26 "웹도 마찬가지로"): a
 * near-full-height bottom sheet on phones, a large centred dialog from `md`. Radix gives ESC, the focus trap, focus return and
 * `aria-labelledby`/`aria-describedby` from the title and time range. Content mounts on open, so each open loads afresh. The web
 * has no sign-in, so no 찜 and no `전체 | 찜한 세션` switch — a footer line points to the app.
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
  return (
    <Sheet open={open && !!schedule} onOpenChange={onOpenChange}>
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
            <div className="min-h-0 flex-1 overflow-auto overscroll-contain px-3">
              <TimetableView activityId={activityId} schedule={schedule} />
            </div>
            <p className="border-t border-separator px-5 pt-3 text-footnote text-secondary-label">세션 찜은 Dearby 앱에서 할 수 있어요.</p>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

/**
 * The timetable of one schedule block, loaded on mount: rows are time slots, columns the tracks in `position` order, an empty slot
 * stays empty and a full-width session (`trackId` null) spans the row. The sheet body scrolls the whole table both ways at once,
 * with the time column and the track row pinned; a wide dialog fits every column without sideways scrolling.
 */
function TimetableView({ activityId, schedule }: { activityId: number; schedule: Schedule }) {
  const [timetable, setTimetable] = useState<Timetable | null>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    request<Timetable>(`/activities/${activityId}/schedules/${schedule.id}/timetable`, { signal: controller.signal })
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
  }, [activityId, schedule.id, attempt]);
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
  return <TimetableTable timetable={timetable} />;
}

function TimetableTable({ timetable }: { timetable: Timetable }) {
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
                      <SessionCard session={wide} time={time} spansRows={span(wide) > 1} />
                    </td>
                  )
                : tracks.map((track) => {
                    const session = at(row, track.id);
                    if (!session) return <td key={track.id} className="rounded-[14px] bg-fill/40" />;
                    if (Date.parse(session.startAt) !== row.start) return null;
                    return (
                      <td key={track.id} rowSpan={span(session)} className="h-px align-top">
                        <SessionCard session={session} track={track} time={time} spansRows={span(session) > 1} />
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

function TrackBadge({ track }: { track: Track }) {
  return (
    <span className={cn("inline-flex max-w-full flex-col rounded-[10px] px-2 py-1 text-caption font-semibold", trackColor(track.position))}>
      <span className="truncate">{track.name}</span>
      {track.room && <span className="truncate font-normal opacity-80">{track.room}</span>}
    </span>
  );
}

/**
 * 쉬는시간·점심 are a muted band; other sessions a card with the track badge, up to two 분야 tags, the title and the speakers. A card
 * over several rows shows its own range, since each time cell shows only its row.
 */
function SessionCard({
  session,
  track,
  time,
  spansRows,
}: {
  session: Session;
  track?: Track;
  time: (value: number) => string;
  spansRows: boolean;
}) {
  const range = `${time(Date.parse(session.startAt))} – ${time(Date.parse(session.endAt))}`;
  if (session.kind === "BREAK" || session.kind === "MEAL")
    return (
      <div className="flex h-full min-h-11 items-center justify-center gap-2 rounded-[14px] bg-fill px-3 py-2 text-subheadline text-secondary-label">
        <span className="font-semibold">{session.title || (session.kind === "MEAL" ? "점심" : "쉬는시간")}</span>
        <span className="text-caption tabular-nums">{range}</span>
      </div>
    );
  return (
    <article className="flex h-full flex-col gap-1.5 rounded-[14px] bg-card p-3">
      <div className="flex flex-wrap gap-1">
        {track && (
          <span className={cn("rounded-full px-2 py-0.5 text-caption2 font-semibold", trackColor(track.position))}>
            {track.name}
            {track.room && ` · ${track.room}`}
          </span>
        )}
        {session.kind === "KEYNOTE" && <Badge variant="filled">키노트</Badge>}
        {session.tags.slice(0, 2).map((tag) => (
          <Badge key={tag.id} className="gap-1 text-caption2">
            <TagIcon nameEn={tag.nameEn} />
            {tag.name}
          </Badge>
        ))}
      </div>
      <h4 className="text-subheadline font-semibold break-keep">{session.title}</h4>
      {session.speakers.length > 0 && (
        <p className="text-footnote text-secondary-label break-keep">
          {session.speakers.map((s) => (s.affiliation ? `${s.name} · ${s.affiliation}` : s.name)).join(", ")}
        </p>
      )}
      {spansRows && <p className="text-caption text-secondary-label tabular-nums">{range}</p>}
    </article>
  );
}
