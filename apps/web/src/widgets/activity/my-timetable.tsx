import type { ReactNode } from "react";
import { type Session, type Track, clockTime } from "@/lib/models";
import { Icon } from "@/shared/ui/icon";
import { SessionCard } from "./session-card";

export type MyTimetableItem = { session: Session; track: Track | null };
export type MyTimetableEntry =
  | { kind: "session"; item: MyTimetableItem }
  /** A warning band before sessions favorited at the same time. */
  | { kind: "overlap"; count: number }
  /** Free minutes before the next session and, when it is on another track, where to move. */
  | { kind: "gap"; minutes: number; moveTo: Track | null };

/**
 * 나만의 시간표 (contract "트랙·세션 시간표" › `찜한 세션` = 나만의 시간표, same rule as iOS `MyTimetable.entries`): sessions sorted by (start,
 * end) and clustered while one starts before the cluster's latest end; between clusters the free time from that end to the next start
 * and a move when the next track differs from the previous cluster's last; an overlap band ahead of a cluster of two or more.
 */
export function myTimetableEntries(items: MyTimetableItem[]): MyTimetableEntry[] {
  const start = (item: MyTimetableItem) => Date.parse(item.session.startAt);
  const end = (item: MyTimetableItem) => Date.parse(item.session.endAt);
  const clusters: MyTimetableItem[][] = [];
  for (const item of [...items].sort((a, b) => start(a) - start(b) || end(a) - end(b))) {
    const last = clusters[clusters.length - 1];
    if (last && start(item) < Math.max(...last.map(end))) last.push(item);
    else clusters.push([item]);
  }
  const entries: MyTimetableEntry[] = [];
  clusters.forEach((cluster, index) => {
    if (index > 0) {
      const previous = clusters[index - 1];
      const minutes = Math.floor((start(cluster[0]) - Math.max(...previous.map(end))) / 60_000);
      const next = cluster[0].track;
      const moved = next !== null && next.id !== previous[previous.length - 1].track?.id;
      if (minutes > 0 || moved) entries.push({ kind: "gap", minutes, moveTo: moved ? next : null });
    }
    if (cluster.length > 1) entries.push({ kind: "overlap", count: cluster.length });
    for (const item of cluster) entries.push({ kind: "session", item });
  });
  return entries;
}

/** `1시간 20분`·`40분`. */
function duration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return hours > 0 ? `${hours}시간${rest > 0 ? ` ${rest}분` : ""}` : `${minutes}분`;
}

/**
 * The favorited sessions of one block (or one day) as a single time-ordered column — no sideways scrolling — each card with its time
 * and a large track·room badge, `n분 비어 있어요 · 이동 › 트랙 B · 201호` between them and `같은 시간에 찜한 세션 n개 · 하나만 들을 수
 * 있어요` ahead of overlaps. `favorite` renders the heart of a session (none for 쉬는시간·점심, which are never favorited).
 */
export function MyTimetableList({ items, timeZone, favorite }: { items: MyTimetableItem[]; timeZone: string; favorite?: (item: MyTimetableItem) => ReactNode }) {
  const time = (value: number) => clockTime(value, timeZone);
  return (
    <ol className="flex flex-col gap-2">
      {myTimetableEntries(items).map((entry, index) => (
        <li key={entry.kind === "session" ? entry.item.session.id : `${entry.kind}-${index}`}>
          {entry.kind === "session" ? (
            <SessionCard session={entry.item.session} track={entry.item.track} time={time} showTime largeTrack favorite={favorite?.(entry.item)} />
          ) : entry.kind === "overlap" ? (
            <p className="flex items-center gap-2 rounded-[10px] bg-danger/12 px-3 py-2.5 text-footnote font-semibold text-danger">
              <Icon name="warning" size="inline" />
              같은 시간에 찜한 세션 {entry.count}개 · 하나만 들을 수 있어요
            </p>
          ) : (
            <p className="flex items-center gap-2 px-2 py-0.5 text-footnote text-secondary-label">
              <Icon name={entry.moveTo ? "map-pin" : "clock"} size="inline" />
              {[entry.minutes > 0 && `${duration(entry.minutes)} 비어 있어요`, entry.moveTo && `이동 › ${[entry.moveTo.name, entry.moveTo.room].filter(Boolean).join(" · ")}`]
                .filter(Boolean)
                .join(" · ")}
            </p>
          )}
        </li>
      ))}
    </ol>
  );
}
