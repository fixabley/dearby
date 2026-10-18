import { z } from "zod";

export const uuid = z.uuid();
const text = z.string();
const contact = z.object({
  id: uuid,
  kind: z.enum(["phone", "email", "kakao", "instagram", "github", "behance"]),
  label: text,
  value: text,
});
// 카탈로그 참조(id는 숫자) 또는 직접 입력한 이름. 명함에는 승인된 이력만 실리고 evidence는 없다.
const activityInfoRef = z.object({ id: z.number().int(), name: text }).nullable();
const history = z.object({
  id: uuid,
  kind: z.enum(["organization", "activity"]),
  organization: activityInfoRef,
  organizationName: text.nullable(),
  program: activityInfoRef,
  programName: text.nullable(),
  activity: activityInfoRef,
  activityName: text.nullable(),
  role: activityInfoRef,
  roleName: text.nullable(),
  startDate: text,
  endDate: text.nullable(),
  description: text,
  approval: z.enum(["approved", "pending", "rejected"]),
  approvalReason: text.nullable(),
  verification: z.enum(["unverified", "pending", "verified", "rejected"]),
});
export type History = z.infer<typeof history>;
// Only the public card projection is accepted. Unknown fields are stripped at the proxy. `introduction` is rendered markdown (the card body).
export const cardSchema = z.object({
  id: uuid,
  name: text,
  description: text,
  profileName: text,
  job: text,
  introduction: text,
  // Optional web-width body (contract "웹 버전"); the API omits it when the card has none.
  contacts: z.array(contact),
  histories: z.array(history),
  createdAt: text,
});
export type Card = z.infer<typeof cardSchema>;
// Public activity from GET /v1/activities (contract "활동 조회 — 2026-10-14"). Timed values carry a zone offset.
const scheduleSchema = z.object({
  id: z.number().int(),
  title: text,
  startAt: text.nullable(),
  endAt: text.nullable(),
  timeZone: text.nullable(),
  startDate: text.nullable(),
  endDateExclusive: text.nullable(),
  dateLabel: text,
  address: text.nullable(),
});
export type Schedule = z.infer<typeof scheduleSchema>;
const named = z.object({ id: z.number().int(), name: text });
export const activitySchema = z.object({
  id: z.number().int().positive(),
  name: text,
  description: text,
  organization: named,
  program: named,
  officialUrl: text,
  imageUrl: text.nullable(),
  application: scheduleSchema.nullable(),
  activitySchedule: scheduleSchema,
  schedules: z.array(scheduleSchema),
});
export const activityPageSchema = z.object({
  items: z.array(activitySchema),
  nextCursor: z.number().int().nullable(),
});
export type Activity = z.infer<typeof activitySchema>;
export type ActivityPage = z.infer<typeof activityPageSchema>;
// Program with open activities from GET /v1/programs (contract "발견 프로그램 묶음 — 2026-10-18").
export const programSummarySchema = z.object({
  id: z.number().int().positive(),
  name: text,
  nameEn: text.nullable(),
  organization: named,
  openActivityCount: z.number().int().nonnegative(),
  nearestRecruitmentEndAt: text.nullable(),
  matchedActivityIds: z.array(z.number().int()),
});
export const programPageSchema = z.object({
  items: z.array(programSummarySchema),
  nextCursor: z.number().int().nullable(),
});
export type ProgramSummary = z.infer<typeof programSummarySchema>;
export type ProgramPage = z.infer<typeof programPageSchema>;
/** `10월 24일 (금) 마감` in Seoul time, or undefined when the end is missing or unreadable. */
export function deadlineLabel(endAt: string | null): string | undefined {
  if (!endAt) return;
  const time = Date.parse(endAt);
  if (Number.isNaN(time)) return;
  return `${new Date(time).toLocaleDateString("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "short",
  })} 마감`;
}
/** Numeric activity ID from a URL segment, or undefined. */
export function activityId(value: string): number | undefined {
  const id = Number(value);
  return /^[1-9]\d*$/.test(value) && Number.isSafeInteger(id) ? id : undefined;
}
/** Recruitment state comes only from the application period; a missing bound is open on that side. */
export function recruitment(
  application: Schedule | null,
  now = Date.now(),
): { open: boolean; label: string } {
  const start = application?.startAt ? Date.parse(application.startAt) : NaN;
  const end = application?.endAt ? Date.parse(application.endAt) : NaN;
  if (Number.isNaN(start) && Number.isNaN(end))
    return { open: false, label: "모집 여부 확인 필요" };
  if (now < start) return { open: false, label: "모집 예정" };
  if (now >= end) return { open: false, label: "모집 마감" };
  return { open: true, label: "모집 중" };
}
const dateTime = (value: string, timeZone: string | null) =>
  new Date(value).toLocaleString("ko-KR", {
    timeZone: timeZone ?? "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "numeric",
    minute: "2-digit",
  });
/** Readable schedule text: the API label first, otherwise its times or all-day start. */
export function scheduleLabel(schedule: Schedule | null): string {
  if (!schedule) return "";
  if (schedule.dateLabel) return schedule.dateLabel;
  const { startAt, endAt, timeZone } = schedule;
  if (startAt || endAt)
    return `${startAt ? dateTime(startAt, timeZone) : ""} ~ ${endAt ? dateTime(endAt, timeZone) : ""}`.trim();
  return schedule.startDate ?? "";
}
/** HTTPS-only link for activity official pages and images. */
export function httpsUrl(value: string | null): string | undefined {
  const url = safeWebUrl(value);
  return url?.startsWith("https:") ? url : undefined;
}
export function safeWebUrl(value: string | null): string | undefined {
  if (!value) return;
  try {
    const url = new URL(value);
    if (
      ["https:", "http:"].includes(url.protocol) &&
      !url.username &&
      !url.password
    )
      return url.href;
  } catch {
    /* Untrusted or missing source URLs are displayed without a link. */
  }
}
export function contactHref(
  contact: Card["contacts"][number],
): string | undefined {
  if (
    contact.kind === "email" &&
    /^[^\s@?&#]+@[^\s@?&#]+\.[^\s@?&#]+$/.test(contact.value)
  )
    return `mailto:${contact.value}`;
  if (contact.kind === "phone" && /^\+?[\d\s()-]+$/.test(contact.value))
    return `tel:${contact.value.replace(/[\s()-]/g, "")}`;
  if (!["email", "phone"].includes(contact.kind))
    return safeWebUrl(contact.value);
}
