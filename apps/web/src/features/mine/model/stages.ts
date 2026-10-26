// 내 활동 진행 트랙 (contract "내 활동 진행 트랙", "선발 과정·활동 일정 분리", "지난 선발 단계 통과 제안", "오늘 내 행사 카드"): the same rules
// as iOS `ActivityStages`·`TodayEvent`, as pure functions over the public Activity and the account's `GET /v1/me/activities` item.
import {
  type Activity,
  type FavoriteSession,
  type MyActivity,
  type Schedule,
  type ScheduleStatus,
  type StatusKind,
  DAY,
  calendarDay,
  clockTime,
  parseTime,
  scheduleLabel,
  seoulDay,
} from "@/lib/models";
import type { IconName } from "@/shared/ui/icon";

const SEOUL = "Asia/Seoul";

/** The user's own mark on a stage: an admin status by id, or a default one by kind; unmarked reads as 대기. Not an organizer result. */
export type Mark = { statusId: number | null; kind: StatusKind };
export const WAIT: Mark = { statusId: null, kind: "WAITING" };
/** The default 대기·통과·탈락 a schedule without admin statuses offers (the server fills them in; older responses send none). */
const DEFAULT_STATUSES: ScheduleStatus[] = [
  { id: null, name: "대기", kind: "WAITING" },
  { id: null, name: "통과", kind: "PASSED" },
  { id: null, name: "탈락", kind: "FAILED" },
];
export function statusOptions(schedule: Schedule): ScheduleStatus[] {
  return schedule.statuses?.length ? schedule.statuses : DEFAULT_STATUSES;
}
/** The option a mark stands for: the same id, else the first of the same kind, else the first option. */
export function markedStatus(stage: Stage): ScheduleStatus {
  const options = statusOptions(stage.schedule);
  return (
    options.find((option) => stage.mark.statusId !== null && option.id === stage.mark.statusId) ??
    options.find((option) => option.kind === stage.mark.kind) ??
    options[0]
  );
}
export function passedOption(stage: Stage): ScheduleStatus | undefined {
  return statusOptions(stage.schedule).find((option) => option.kind === "PASSED");
}

export type StageState = "past" | "current" | "upcoming";
export type Stage = {
  /** The schedule id the mark is keyed by (`PUT …/schedules/{scheduleId}`). */
  id: number;
  /** The `지원` stage (the recruitment window; its date is the deadline alone). */
  application: boolean;
  name: string;
  /** Badge suffix: 마감·발표·면접, the title for events, `지원`, the period's name. */
  badgeName: string;
  icon: IconName;
  schedule: Schedule;
  /** 선발 과정 stage: only these take 통과·대기·탈락; an 활동 일정 stage keeps the date and 예정/지남 and its stored mark is not read. */
  selection: boolean;
  /** `활동 일정` section: activity stages and, whatever its phase, any block with a timetable. */
  inActivitySection: boolean;
  mark: Mark;
  state: StageState;
  /** The Seoul day the stage counts from (its start, or its end when only that is known); undefined when date-less. */
  anchorDay?: string;
  /** Sort key for the same anchor. */
  anchorTime: number;
};
export type Progress = {
  stages: Stage[];
  /** undefined when every stage is past or a 탈락 is marked: the activity belongs to `끝난 활동`. */
  currentIndex?: number;
  failed: boolean;
  /** Every 선발 단계 is marked 통과 and a later stage remains (참여 중). */
  participating: boolean;
};
export const ended = (progress: Progress) => progress.currentIndex === undefined;
export const current = (progress: Progress) => (progress.currentIndex === undefined ? undefined : progress.stages[progress.currentIndex]);

/** Whole Seoul days from today to a `yyyy-MM-dd` day; negative when it passed. */
export function daysUntil(day: string, now: number): number {
  return Math.round((Date.parse(day) - Date.parse(seoulDay(now))) / DAY);
}
const allDay = (schedule: Schedule) => !schedule.startAt && !schedule.endAt;
/** The Seoul day before a `yyyy-MM-dd` (an exclusive end's last day). */
const dayBefore = (day: string) => calendarDay(Date.parse(day) - DAY, "UTC");
/** `yyyy-MM-dd` of a schedule's start (all-day: its date; timed: the Seoul day of the instant). */
function startDay(schedule: Schedule): string | undefined {
  if (allDay(schedule)) return schedule.startDate ?? undefined;
  const start = parseTime(schedule.startAt);
  return start === undefined ? undefined : calendarDay(start, SEOUL);
}
/** The last Seoul day a schedule covers (all-day: exclusive end minus a day; timed: the end's day, else the start's). */
function lastDay(schedule: Schedule): string | undefined {
  if (allDay(schedule)) return schedule.endDateExclusive ? dayBefore(schedule.endDateExclusive) : schedule.startDate ?? undefined;
  const end = parseTime(schedule.endAt) ?? parseTime(schedule.startAt);
  return end === undefined ? undefined : calendarDay(end, SEOUL);
}
/**
 * Over by the track's Seoul-day rule (iOS `finished`): timed once its end (its start with no end) fell before today; all-day once the
 * exclusive end (its start with no end) reached today; date-less never.
 */
function finished(schedule: Schedule, deadlineOnly: boolean, today: string): boolean {
  if (allDay(schedule)) {
    const bound = schedule.endDateExclusive ?? (deadlineOnly ? null : schedule.startDate);
    return !!bound && bound <= today;
  }
  const bound = parseTime(schedule.endAt) ?? (deadlineOnly ? undefined : parseTime(schedule.startAt));
  return bound !== undefined && calendarDay(bound, SEOUL) < today;
}
/**
 * Past by the instant (contract "지난 선발 단계 통과 제안"): the end (start with no end) is behind `now`; an all-day stage once the Seoul
 * midnight that closes it has come. undefined for a date-less stage.
 */
function pastInstant(schedule: Schedule, deadlineOnly: boolean, now: number): boolean | undefined {
  if (allDay(schedule)) {
    const bound = schedule.endDateExclusive ?? (deadlineOnly ? null : schedule.startDate);
    return bound ? Date.parse(`${bound}T00:00:00+09:00`) <= now : undefined;
  }
  const bound = parseTime(schedule.endAt) ?? (deadlineOnly ? undefined : parseTime(schedule.startAt));
  return bound === undefined ? undefined : bound < now;
}

/**
 * 선발 과정 (contract "선발 과정·활동 일정 분리"): the server's `phase`; an older server sends none, and then the superseded rule decides —
 * 마감·발표·면접, or an event whose statuses the admin set (an id). The `지원` stage always is; the activity period never.
 */
function isSelection(schedule: Schedule): boolean {
  if (schedule.phase) return schedule.phase === "SELECTION";
  return (schedule.kind !== undefined && schedule.kind !== "EVENT") || !!schedule.statuses?.some((status) => status.id !== null);
}
const BADGE_NAME = { DEADLINE: "마감", ANNOUNCEMENT: "발표", INTERVIEW: "면접" } as const;
const ICON: Record<NonNullable<Schedule["kind"]>, IconName> = { DEADLINE: "flag", ANNOUNCEMENT: "megaphone", INTERVIEW: "people-pair", EVENT: "calendar" };

/** Structured dates, else the collected label (contract "일정 표시 우선순위"); `날짜 미확인` with neither. `지원` is the deadline (`~ … 마감`). */
export function stageDate(stage: Stage, now: number): string {
  const text = stage.application
    ? scheduleLabel({ ...stage.schedule, startAt: null, startDate: null }, "recruitment", now)
    : scheduleLabel(stage.schedule, "activity", now);
  return text || "날짜 미확인";
}

/**
 * Builds the track (contract "내 활동 진행 트랙" › 단계 만들기): `지원` first, `schedules[]` by start with date-less ones after in API order,
 * the activity period last. The current stage is the first neither past by Seoul day nor marked 통과; the track ends when every stage
 * is over or a 탈락 is marked. `item` carries the account's marks (none → every stage 대기).
 */
export function buildProgress(activity: Activity, item: MyActivity | undefined, now: number): Progress {
  const marks = new Map<number, Mark>(item?.statuses.map((pick) => [pick.scheduleId, { statusId: pick.statusId, kind: pick.kind }]));
  const stage = (
    schedule: Schedule,
    name: string,
    badgeName: string,
    icon: IconName,
    selection: boolean,
    application = false,
  ): Stage => {
    const day = application ? lastDay(schedule) : startDay(schedule) ?? lastDay(schedule);
    const anchorTime = allDay(schedule)
      ? day === undefined
        ? Infinity
        : Date.parse(day)
      : parseTime(application ? schedule.endAt : schedule.startAt ?? schedule.endAt) ?? Infinity;
    return {
      id: schedule.id,
      application,
      name,
      badgeName,
      icon,
      schedule,
      selection,
      inActivitySection: !selection || !!schedule.hasTimetable,
      mark: selection ? marks.get(schedule.id) ?? WAIT : WAIT,
      state: "upcoming",
      anchorDay: day,
      anchorTime,
    };
  };
  const stages: Stage[] = [];
  if (activity.application) stages.push(stage(activity.application, "지원", "지원", "paperplane", true, true));
  const detail = activity.schedules.map((schedule) =>
    stage(schedule, schedule.title, schedule.kind && schedule.kind !== "EVENT" ? BADGE_NAME[schedule.kind] : schedule.title, ICON[schedule.kind ?? "EVENT"], isSelection(schedule)),
  );
  // Stable sort: by anchor, date-less last in API order.
  stages.push(...detail.map((each, index) => [each, index] as const).sort((a, b) => a[0].anchorTime - b[0].anchorTime || a[1] - b[1]).map(([each]) => each));
  const periodName = activity.activitySchedule.title || "활동";
  stages.push(stage(activity.activitySchedule, periodName, periodName, "flag", false));
  const today = seoulDay(now);
  let currentIndex: number | undefined;
  for (const [index, each] of stages.entries()) {
    const done = each.mark.kind === "PASSED" || finished(each.schedule, each.application, today);
    if (currentIndex !== undefined) break;
    if (done) each.state = "past";
    else {
      currentIndex = index;
      each.state = "current";
    }
  }
  const failed = stages.some((each) => each.mark.kind === "FAILED");
  const selections = stages.filter((each) => each.selection);
  const participating =
    !failed && selections.length > 0 && selections.length < stages.length && selections.every((each) => each.mark.kind === "PASSED");
  return { stages, currentIndex: failed ? undefined : currentIndex, failed, participating };
}

/**
 * `D-3 · 면접`; `D-DAY · 발표` while the current stage is ongoing; `날짜 미확인 · 발표` without a date; `참여 중` once every 선발 단계 is passed;
 * ended tracks read `지남` or `탈락`.
 */
export function progressBadge(progress: Progress, now: number): string {
  const stage = current(progress);
  if (!stage) return progress.failed ? "탈락" : "지남";
  if (progress.participating) return "참여 중";
  if (!stage.anchorDay) return `날짜 미확인 · ${stage.badgeName}`;
  const days = daysUntil(stage.anchorDay, now);
  return `${days > 0 ? `D-${days}` : "D-DAY"} · ${stage.badgeName}`;
}
/** The whole track as one element: "5단계 중 3단계 1차 면접 진행 중". */
export function progressLabel(progress: Progress): string {
  const stage = current(progress);
  if (!stage || progress.currentIndex === undefined)
    return progress.failed ? `${progress.stages.length}단계 중 탈락 표시` : `${progress.stages.length}단계 모두 지남`;
  if (progress.participating) return "선발 단계 모두 통과, 참여 중";
  return `${progress.stages.length}단계 중 ${progress.currentIndex + 1}단계 ${stage.name} 진행 중`;
}
/** List order (contract "내 활동 진행 트랙" › 검색·정렬): the current stage's day; undefined (date-less or ended) sorts last. */
export function nextStart(progress: Progress): number {
  const stage = current(progress);
  return stage?.anchorDay ? Date.parse(stage.anchorDay) : Infinity;
}

/**
 * 지난 선발 단계 통과 제안 (contract "지난 선발 단계 통과 제안" 2026-10-26): the 선발 단계 in track order up to the first dated one that is not
 * yet past — the past dated ones and the date-less ones between them. Only unmarked stages with a 통과 option; empty when no dated
 * stage is past.
 */
export function autopassCandidates(progress: Progress, now: number): Stage[] {
  const candidates: Stage[] = [];
  let pastDated = false;
  for (const stage of progress.stages) {
    if (!stage.selection) continue;
    const past = pastInstant(stage.schedule, stage.application, now);
    if (past === false) break;
    if (past) pastDated = true;
    if (stage.mark.kind === "WAITING" && stage.mark.statusId === null && passedOption(stage)) candidates.push(stage);
  }
  return pastDated ? candidates : [];
}
/** The suggestion's body: the two contract sentences, depending on whether 선발 단계 remain. */
export function autopassMessage(stages: Stage[], remaining: Stage[]): string {
  const names = stages.map((stage) => stage.name).join(", ");
  const outcome = remaining.length
    ? `통과로 표시해요. 남은 단계(${remaining.map((stage) => stage.name).join(", ")})는 그대로 둬요.`
    : "통과로 표시하면 바로 참여 중으로 보여요.";
  return `${stages.length}개 단계(${names})가 이미 지났어요. ${outcome}`;
}

// 오늘 내 행사 (contract "오늘 내 행사 카드"): the one applied activity whose 활동 일정 block is today or starts within 7 days.
export type TodayEvent = {
  activity: Activity;
  /** 0 = today; 1…7 = Seoul days until the block's first day. */
  days: number;
  block: Schedule;
  /** Today only: the first favorited session of this activity today that has not ended. */
  session?: FavoriteSession;
  /** Other applied activities that also qualify (`외 n개`). */
  others: number;
};
/** The Seoul days a block covers, when it has a start, ends after it starts and spans at most 7 days (a months-long period is no event day). */
function blockDays(schedule: Schedule): { first: string; last: string } | undefined {
  const first = startDay(schedule);
  const last = lastDay(schedule);
  if (!first || !last || last < first || (Date.parse(last) - Date.parse(first)) / DAY > 7) return;
  return { first, last };
}
export function pickTodayEvent(applied: { activity: Activity; progress: Progress }[], favorites: FavoriteSession[], now: number): TodayEvent | undefined {
  const today = seoulDay(now);
  const found: { event: TodayEvent; rank: number; start: number }[] = [];
  for (const { activity, progress } of applied) {
    if (ended(progress)) continue;
    const blocks = progress.stages
      .filter((stage) => stage.inActivitySection)
      .flatMap((stage) => {
        const days = blockDays(stage.schedule);
        return days ? [{ block: stage.schedule, ...days }] : [];
      })
      .sort((a, b) => a.first.localeCompare(b.first) || (parseTime(a.block.startAt) ?? 0) - (parseTime(b.block.startAt) ?? 0));
    const todays = blocks.filter(({ first, last }) => first <= today && today <= last).map(({ block }) => block);
    if (todays.length) {
      const mine = favorites
        .filter((favorite) => favorite.activityId === activity.id && calendarDay(Date.parse(favorite.session.startAt), SEOUL) === today)
        .sort((a, b) => Date.parse(a.session.startAt) - Date.parse(b.session.startAt));
      const session = mine.find((favorite) => Date.parse(favorite.session.endAt) > now);
      const block =
        (session && todays.find((each) => each.id === session.scheduleId)) ??
        todays.find((each) => allDay(each) || (parseTime(each.endAt) ?? Infinity) > now) ??
        todays[todays.length - 1];
      found.push({ event: { activity, days: 0, block, session, others: 0 }, rank: 0, start: parseTime(block.startAt) ?? Date.parse(today) });
    } else {
      const ahead = blocks.find(({ first }) => first > today);
      if (!ahead) continue;
      const days = daysUntil(ahead.first, now);
      if (days > 7) continue;
      found.push({ event: { activity, days, block: ahead.block, others: 0 }, rank: 1, start: parseTime(ahead.block.startAt) ?? Date.parse(ahead.first) });
    }
  }
  found.sort((a, b) => a.rank - b.rank || a.start - b.start || a.event.activity.id - b.event.activity.id);
  const first = found[0]?.event;
  return first && { ...first, others: found.length - 1 };
}
/** The block's title, `활동` for the untitled activity period (as the track names it). */
export const blockTitle = (block: Schedule) => block.title || "활동";
/**
 * The card's body: today, the next session (`14:50 · 트랙 B · 그랜드볼룸 B · 세션 제목`, `지금 · …` while it runs) or the block
 * (`오후 세션 13:50–17:30`, `활동 · 종일`); upcoming, the block's title and its date and time.
 */
export function todayDetail(event: TodayEvent, now: number): string {
  const { block, session } = event;
  if (event.days > 0) return `${blockTitle(block)} · ${scheduleLabel(block, "activity", now)}`;
  if (session) {
    const zone = session.schedule?.timeZone ?? block.timeZone ?? SEOUL;
    const start = Date.parse(session.session.startAt);
    return [start <= now ? "지금" : clockTime(start, zone), session.track?.name, session.track?.room, session.session.title].filter(Boolean).join(" · ");
  }
  const start = parseTime(block.startAt);
  if (allDay(block) || start === undefined) return `${blockTitle(block)} · 종일`;
  const zone = block.timeZone ?? SEOUL;
  const end = parseTime(block.endAt);
  return `${blockTitle(block)} ${clockTime(start, zone)}${end === undefined ? "" : `–${clockTime(end, zone)}`}`;
}
