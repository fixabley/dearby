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
// 명함 교환(contract "명함 교환 — 2026-10-19"): where a saved card was exchanged and when the server recalls it.
const exchangeContext = z.object({
  activityId: z.number().int().nullable(),
  activityName: text.nullable(),
  programName: text.nullable(),
});
export const guestItemSchema = z.object({
  card: cardSchema,
  context: exchangeContext,
  savedAt: text,
  expiresAt: text,
});
export const guestListSchema = z.object({ items: z.array(guestItemSchema) });
export type GuestItem = z.infer<typeof guestItemSchema>;
/** `N일 뒤 사라져요`, or `오늘 사라져요` when the recall is today or already due. */
export function expiryLabel(expiresAt: string, now = Date.now()): string {
  const time = Date.parse(expiresAt);
  if (Number.isNaN(time)) return "";
  const days = Math.ceil((time - now) / 86_400_000);
  return days <= 0 ? "오늘 사라져요" : `${days}일 뒤 사라져요`;
}
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
// Eligibility (contract "대상자 조건 — 2026-10-18"): nested all/any groups of conditions with Korean
// vocabulary plus English twins (`fieldEn`, `valuesEn`, `noteEn`); ranges are `from`/`to`. JSON keys are camelCase; snake_case lives only in the DB.
const eligibilityConditionSchema = z.object({
  field: text,
  fieldEn: text,
  from: z.number().optional(),
  to: z.number().optional(),
  values: z.array(text).optional(),
  valuesEn: z.array(text).optional(),
  note: text.optional(),
  noteEn: text.optional(),
});
export type EligibilityCondition = z.infer<typeof eligibilityConditionSchema>;
export type Eligibility = {
  op: "all" | "any";
  items: (Eligibility | EligibilityCondition)[];
};
const eligibilitySchema: z.ZodType<Eligibility> = z.lazy(() =>
  z.object({
    op: z.enum(["all", "any"]),
    items: z.array(z.union([eligibilitySchema, eligibilityConditionSchema])),
  }),
);
// 얻을 수 있는 것 태그 (contract "태그"): dictionary entries; older responses omit the list.
export const tagSchema = z.object({ id: z.number().int().positive(), name: text, nameEn: text.nullable().optional() });
export const tagListSchema = z.object({ items: z.array(tagSchema) });
export type Tag = z.infer<typeof tagSchema>;
const tags = z.array(tagSchema).optional().catch([]);
const placeSchema = z.object({ name: text, address: text, lat: z.number(), lng: z.number() });
export type Place = z.infer<typeof placeSchema>;
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
  // 활동 종류 code (contract "활동 종류"); older responses omit it.
  category: text.nullable().optional().catch(null),
  tags,
  // A malformed eligibility must not break the activity.
  eligibility: eligibilitySchema.nullable().optional().catch(null),
  // 활동 장소 (contract "활동 장소 — 2026-10-19"): the collected phrase and the curated place; older responses omit both.
  location: text.nullable().optional().catch(null),
  place: placeSchema.nullable().optional().catch(null),
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
  /** Latest activity start among the program's published activities; absent on older responses (contract "마감 숨기기"). */
  latestActivityAt: text.nullable().optional().catch(null),
  /** 모집 예정 activities and the earliest of their recruitment (else activity) starts; absent on older responses (contract "마감 숨기기", 2026-10-19 밤). */
  upcomingActivityCount: z.number().int().nonnegative().optional().catch(0),
  nearestRecruitmentStartAt: text.nullable().optional().catch(null),
  /** Program tags, else the union of its open activities' tags (contract "태그"). */
  tags,
});
export const programPageSchema = z.object({
  items: z.array(programSummarySchema),
  nextCursor: z.number().int().nullable(),
});
export type ProgramSummary = z.infer<typeof programSummarySchema>;
export type ProgramPage = z.infer<typeof programPageSchema>;
/** `10월 24일 마감` in Seoul time (contract "발견 카드 리뉴얼" meta line), or undefined when the end is missing or unreadable. */
export function deadlineLabel(endAt: string | null): string | undefined {
  const day = monthDay(endAt);
  return day && `${day} 마감`;
}
/** `10월 24일 시작` in Seoul time, or undefined when the start is missing or unreadable. */
export function startLabel(startAt: string | null): string | undefined {
  const day = monthDay(startAt);
  return day && `${day} 시작`;
}
function monthDay(value: string | null): string | undefined {
  if (!value) return;
  const time = Date.parse(value);
  if (Number.isNaN(time)) return;
  return new Date(time).toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric" });
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
  if (!application) return { open: false, label: "모집 여부 확인 필요" };
  // Date-only windows compare Seoul days: before the start → 예정, before the exclusive end → 중, otherwise 마감
  // (contract "열린 활동 판정 일원화"). Timed windows compare instants; a missing end or start is open on that side.
  if (!application.startAt && !application.endAt) {
    if (!application.startDate && !application.endDateExclusive)
      return { open: false, label: "모집 여부 확인 필요" };
    const today = seoulDay(now);
    if (application.startDate && today < application.startDate) return { open: false, label: "모집 예정" };
    if (application.endDateExclusive && today >= application.endDateExclusive) return { open: false, label: "모집 마감" };
    return { open: true, label: "모집 중" };
  }
  const start = application.startAt ? Date.parse(application.startAt) : NaN;
  const end = application.endAt ? Date.parse(application.endAt) : NaN;
  if (now < start) return { open: false, label: "모집 예정" };
  if (now >= end) return { open: false, label: "모집 마감" };
  return { open: true, label: "모집 중" };
}
/** `yyyy-MM-dd` of an instant in Asia/Seoul, comparable with the API's date-only fields. */
function seoulDay(now: number): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(now));
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
