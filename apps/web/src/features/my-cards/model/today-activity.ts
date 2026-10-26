import type { Activity, Schedule } from "@/lib/models";

/** `yyyy-MM-dd` of an instant in Asia/Seoul. */
function seoulDay(time: number): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(time));
}
const DAY = 86_400_000;

/** The 활동 일정 blocks of an activity: the activity period and the detail schedules of phase ACTIVITY (or without a phase, older servers). */
function activityBlocks(activity: Activity): Schedule[] {
  return [activity.activitySchedule, ...activity.schedules.filter((schedule) => schedule.phase === undefined || schedule.phase === "ACTIVITY")];
}

/** Length of a block in ms when it covers `today` (Seoul date), else undefined. */
function coveringLength(block: Schedule, today: string): number | undefined {
  const startAt = block.startAt ? Date.parse(block.startAt) : NaN;
  const endAt = block.endAt ? Date.parse(block.endAt) : NaN;
  if (!Number.isNaN(startAt) || !Number.isNaN(endAt)) {
    const from = Number.isNaN(startAt) ? endAt : startAt;
    const to = Number.isNaN(endAt) ? startAt : endAt;
    if (seoulDay(from) <= today && today <= seoulDay(to)) return Math.max(to - from, 0);
    return;
  }
  if (block.startDate && block.endDateExclusive) {
    if (block.startDate <= today && today < block.endDateExclusive) return Date.parse(block.endDateExclusive) - Date.parse(block.startDate);
    return;
  }
  if (block.startDate && block.startDate === today) return DAY;
}

/**
 * The activity to preselect on the QR screen (contract "명함 교환" › 주인: 오늘 진행 중인 내 활동): among my activities, the one whose
 * 활동 일정 block covers today in Seoul, preferring the shortest block (a session day over a months-long period); undefined when none.
 */
export function todayActivity(activities: Activity[], now = Date.now()): Activity | undefined {
  const today = seoulDay(now);
  let best: { activity: Activity; length: number } | undefined;
  for (const activity of activities)
    for (const block of activityBlocks(activity)) {
      const length = coveringLength(block, today);
      if (length !== undefined && (!best || length < best.length)) best = { activity, length };
    }
  return best?.activity;
}
