import type { ReactNode } from "react";
import type { Session, Track } from "@/lib/models";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Icon } from "@/shared/ui/icon";
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
export const trackColor = (position: number) => TRACK_COLORS[position % TRACK_COLORS.length];

/**
 * One cell's worth of the table: a real session, or in the day timetable a whole block without a timetable (`block: "plain"`,
 * the block title as a muted band) or one whose timetable failed to load (`block: "failed"`).
 */
export type Entry = Session & { block?: "plain" | "failed" };

/** 쉬는시간·점심 and whole-block bands carry no heart (contract "트랙·세션 시간표" › 내 시간표). */
export const isBreak = (session: Entry) => !!session.block || session.kind === "BREAK" || session.kind === "MEAL";

/** The column heading of a track: name over room. */
export function TrackBadge({ track }: { track: Track }) {
  return (
    <span className={cn("inline-flex max-w-full flex-col rounded-[10px] px-2 py-1 text-caption font-semibold", trackColor(track.position))}>
      <span className="truncate">{track.name}</span>
      {track.room && <span className="truncate font-normal opacity-80">{track.room}</span>}
    </span>
  );
}

/**
 * 쉬는시간·점심 and whole blocks without a timetable are a muted band (a failed block adds `불러오지 못했어요`); other sessions a card
 * with the track badge, up to two 분야 tags, the title, the speakers and the heart (`favorite` slot). A card over several rows shows
 * its own range, since each time cell shows only its row; `showTime` always shows it and `largeTrack` enlarges the track·room
 * (내 시간표: where to walk to).
 */
export function SessionCard({
  session,
  track,
  time,
  spansRows = false,
  showTime = false,
  largeTrack = false,
  favorite,
}: {
  session: Entry;
  track?: Track | null;
  time: (value: number) => string;
  spansRows?: boolean;
  showTime?: boolean;
  largeTrack?: boolean;
  favorite?: ReactNode;
}) {
  const range = `${time(Date.parse(session.startAt))} – ${time(Date.parse(session.endAt))}`;
  if (isBreak(session))
    return (
      <div className="flex h-full min-h-11 flex-wrap items-center justify-center gap-x-2 gap-y-0.5 rounded-[14px] bg-fill px-3 py-2 text-subheadline text-secondary-label">
        <span className="font-semibold break-keep">{session.title || (session.kind === "MEAL" ? "점심" : "쉬는시간")}</span>
        <span className="text-caption tabular-nums">{range}</span>
        {session.block === "failed" && (
          <span className="flex items-center gap-1 text-caption text-danger">
            <Icon name="warning" size="inline" />
            불러오지 못했어요
          </span>
        )}
      </div>
    );
  return (
    <article className="flex h-full flex-col gap-1.5 rounded-[14px] bg-card p-3">
      {showTime && <p className="text-caption font-semibold text-secondary-label tabular-nums">{range}</p>}
      <div className="flex items-start gap-1">
        <div className="flex min-w-0 flex-1 flex-wrap gap-1">
          {track && (
            <span
              className={cn(
                "rounded-full font-semibold",
                largeTrack ? "px-2.5 py-1 text-subheadline" : "px-2 py-0.5 text-caption2",
                trackColor(track.position),
              )}
            >
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
        {favorite && <span className="-my-1.5 -mr-1 flex shrink-0">{favorite}</span>}
      </div>
      <h4 className="text-subheadline font-semibold break-keep">{session.title}</h4>
      {session.speakers.length > 0 && (
        <p className="text-footnote text-secondary-label break-keep">
          {session.speakers.map((s) => (s.affiliation ? `${s.name} · ${s.affiliation}` : s.name)).join(", ")}
        </p>
      )}
      {spansRows && !showTime && <p className="text-caption text-secondary-label tabular-nums">{range}</p>}
    </article>
  );
}
