import { z } from "zod";

export const uuid = z.uuid();
const text = z.string();
export const CONTACT_KINDS = ["phone", "email", "kakao", "instagram", "github", "behance"] as const;
export type ContactKind = (typeof CONTACT_KINDS)[number];
// 연락처 인증(contract "연락처 인증" 2026-10-26): `verified` is set by the server alone; older responses omit both fields.
export const contactSchema = z.object({
  id: uuid,
  kind: z.enum(CONTACT_KINDS),
  label: text,
  value: text,
  verified: z.boolean().optional().catch(false),
  verifiedAt: text.nullable().optional().catch(null),
});
export type Contact = z.infer<typeof contactSchema>;
const contact = contactSchema;
// 카탈로그 참조(id는 숫자) 또는 직접 입력한 이름. 명함에는 승인된 이력만 실리고 evidence는 없다.
const activityInfoRef = z.object({ id: z.number().int(), name: text }).nullable();
export const historySchema = z.object({
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
const history = historySchema;
export type History = z.infer<typeof historySchema>;
// Only the public card projection is accepted. Unknown fields are stripped at the proxy. `introLink` is the 소개 링크 next to the name
// (contract "소개 링크·소개 글 폐지"); the old `introduction` body is no longer shown.
export const cardSchema = z.object({
  id: uuid,
  name: text,
  description: text,
  profileName: text,
  job: text,
  introLink: text.nullable().optional(),
  contacts: z.array(contact),
  histories: z.array(history),
  createdAt: text,
  // "/v1/cards/:id/photo?v=<ms>" or null (contract "프로필 사진"); signed-in lists load it through `/api/cards/:id/photo` (`cardPhotoSrc`).
  photoUrl: text.nullable().optional().catch(null),
  // 명함 사진 on the public page: the proxy fetches `photoUrl` with the same view right and inlines it, because the page drops `code` from its address.
  // Signed-in screens put the same-origin proxy URL here instead.
  photo: z.string().optional(),
});
export type Card = z.infer<typeof cardSchema>;
// 명함 교환(contract "명함 교환 — 2026-10-19"): where a saved card was exchanged and when the server recalls it.
// 교환 세션 맥락(contract "교환 세션 맥락", 2026-10-26): optional until the server sends them; absent on older receipts.
const sessionField = text.nullable().optional().catch(undefined);
export const exchangeContextSchema = z.object({
  activityId: z.number().int().nullable(),
  activityName: text.nullable(),
  programName: text.nullable(),
  scheduleTitle: sessionField,
  sessionTitle: sessionField,
  trackName: sessionField,
  room: sessionField,
});
const exchangeContext = exchangeContextSchema;
export type ExchangeContext = z.infer<typeof exchangeContextSchema>;
export const guestItemSchema = z.object({
  card: cardSchema,
  context: exchangeContext,
  savedAt: text,
  expiresAt: text,
});
export const guestListSchema = z.object({ items: z.array(guestItemSchema) });
export type GuestItem = z.infer<typeof guestItemSchema>;
/**
 * `우아콘 2026 · 오후 세션 · 트랙 B · 그랜드볼룸 B`, then the session title as a second line; empty when the receipt carries no
 * session fields, so a plain program chip stays as it was.
 */
export function sessionContextLines({ programName, scheduleTitle, sessionTitle, trackName, room }: ExchangeContext): string[] {
  if (!scheduleTitle && !sessionTitle && !trackName && !room) return [];
  return [[programName, scheduleTitle, trackName, room].filter(Boolean).join(" · "), sessionTitle].filter((line): line is string => !!line);
}
/** `N일 뒤 사라져요`, or `오늘 사라져요` when the recall is today or already due. */
export function expiryLabel(expiresAt: string, now = Date.now()): string {
  const time = Date.parse(expiresAt);
  if (Number.isNaN(time)) return "";
  const days = Math.ceil((time - now) / 86_400_000);
  return days <= 0 ? "오늘 사라져요" : `${days}일 뒤 사라져요`;
}
/** 활동 상태 (contract "활동 상태" 2026-10-26): the server judges it in Seoul time; the web only shows it. */
export const ACTIVITY_STATUSES = [
  "RECRUITING_UPCOMING",
  "RECRUITING",
  "RECRUITMENT_CLOSED",
  "NO_RECRUITMENT",
  "IN_PROGRESS",
  "ENDED",
] as const;
export type ActivityStatus = (typeof ACTIVITY_STATUSES)[number];
/** Badge text; 모집 정보 없음 has none (its date reads `모집 일정 미정`). */
export const STATUS_BADGE: Record<ActivityStatus, string | undefined> = {
  RECRUITING_UPCOMING: "모집 예정",
  RECRUITING: "모집 중",
  RECRUITMENT_CLOSED: "모집 마감",
  NO_RECRUITMENT: undefined,
  IN_PROGRESS: "진행 중",
  ENDED: "종료",
};
/** 모집 중·모집 예정: the recruitment row stands out and the CTA applies. */
export function applicable(status: ActivityStatus | undefined): boolean {
  return status === "RECRUITING" || status === "RECRUITING_UPCOMING";
}
/** 발견 상태 필터의 세 묶음 (contract "활동 상태"): ids are the `?status=` URL values and the GA `groups` values. */
/**
 * 발견 상태 필터의 세 묶음 (contract "활동 상태", regrouped 2026-10-26 by the activity's own schedule): 지원 가능 = applying ahead or open;
 * 진행·예정 = every activity that has not ended (the server judges ENDED first, so 모집 마감·모집 정보 없음 are unfinished ones); 지난 활동 = ENDED.
 * The ids stay (`in_progress` keeps old `?status=` links).
 */
export const STATUS_GROUPS = {
  available: { label: "지원 가능", statuses: ["RECRUITING_UPCOMING", "RECRUITING"] },
  in_progress: { label: "진행·예정", statuses: ["IN_PROGRESS", "RECRUITMENT_CLOSED", "NO_RECRUITMENT"] },
  past: { label: "지난 활동", statuses: ["ENDED"] },
} as const satisfies Record<string, { label: string; statuses: readonly ActivityStatus[] }>;
export type StatusGroup = keyof typeof STATUS_GROUPS;
export const STATUS_GROUP_IDS = Object.keys(STATUS_GROUPS) as StatusGroup[];
/** `?status=` as groups in display order; nothing valid → `지원 가능`. */
export function parseStatusGroups(value: string | undefined): StatusGroup[] {
  const ids = new Set(value?.split(","));
  const groups = STATUS_GROUP_IDS.filter((id) => ids.has(id));
  return groups.length ? groups : ["available"];
}
export function groupStatuses(groups: StatusGroup[]): ActivityStatus[] {
  return groups.flatMap((group) => STATUS_GROUPS[group].statuses);
}
// 일정 상태 (contract "내 활동 서버 저장·일정 상태"): the options a stage can be marked with — the admin's (with an id) or the default
// 대기·통과·탈락 the server fills in (id null). `kind` drives the track; `name` is what the picker shows.
export const STATUS_KINDS = ["WAITING", "PASSED", "FAILED", "OTHER"] as const;
export type StatusKind = (typeof STATUS_KINDS)[number];
const scheduleStatusSchema = z.object({ id: z.number().int().nullable(), name: text, kind: z.enum(STATUS_KINDS) });
export type ScheduleStatus = z.infer<typeof scheduleStatusSchema>;
export const SCHEDULE_KINDS = ["DEADLINE", "ANNOUNCEMENT", "INTERVIEW", "EVENT"] as const;
// Public activity from GET /v1/activities (contract "활동 조회 — 2026-10-14"). Timed values carry a zone offset.
const scheduleSchema = z.object({
  id: z.number().int(),
  title: text,
  // 종류 (contract "내 활동 진행 트랙": 마감·발표·면접 or an event); absent on older responses.
  kind: z.enum(SCHEDULE_KINDS).optional().catch(undefined),
  statuses: z.array(scheduleStatusSchema).optional().catch([]),
  startAt: text.nullable(),
  endAt: text.nullable(),
  timeZone: text.nullable(),
  startDate: text.nullable(),
  endDateExclusive: text.nullable(),
  dateLabel: text,
  // The API sends `{addressLine1, addressLine2, lat, lng}` since schedules carry places; older responses a string. Shown as one line.
  address: z
    .union([
      text,
      z
        .object({ addressLine1: text.nullable().optional(), addressLine2: text.nullable().optional() })
        .transform((value) => [value.addressLine1, value.addressLine2].filter(Boolean).join(", ") || null),
    ])
    .nullable()
    .catch(null),
  // 이 블록에 세션이 하나라도 있음(contract "트랙·세션 시간표"); older responses omit it.
  hasTimetable: z.boolean().optional().catch(false),
  // 선발 과정·활동 일정 (contract "선발 과정·활동 일정 분리" 2026-10-26); stays undefined on older responses so the detail keeps one list.
  phase: z.enum(["SELECTION", "ACTIVITY"]).optional().catch(undefined),
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
// 태그 (contract "태그"): dictionary entries; older responses omit the list. `kind` (contract "분야·성격 태그와 아이콘"):
// FIELD 분야 | TRAIT 성격(얻을 수 있는 것); missing or unknown means TRAIT.
export const tagSchema = z.object({
  id: z.number().int().positive(),
  name: text,
  nameEn: text.nullable().optional(),
  kind: z.enum(["FIELD", "TRAIT"]).optional().catch(undefined),
  // 상위 분야 id; display ignores it (the server already orders fieldTags).
  parentId: z.number().int().nullable().optional().catch(null),
});
export type Tag = z.infer<typeof tagSchema>;
const tags = z.array(tagSchema).optional().catch([]);
// Kept `undefined` when absent so an older response falls back to `tags` (see `traitTagsOf`).
const splitTags = z.array(tagSchema).optional().catch(undefined);
/** 분야 tags the server sorted (at most two on a summary); none on older responses. */
export function fieldTagsOf(item: { fieldTags?: Tag[] }): Tag[] {
  return item.fieldTags ?? [];
}
/** 성격 tags (얻을 수 있는 것); older responses only send `tags`, which were all traits then. */
export function traitTagsOf(item: { traitTags?: Tag[]; tags?: Tag[] }): Tag[] {
  return item.traitTags ?? item.tags ?? [];
}
// 즐겨찾기·세션 찜 (contract "즐겨찾기(하트)", "트랙·세션 시간표", web since "웹 화면 — 2단계 A"): true only on a signed-in response
// (the proxy sends the access cookie as Bearer); anonymous and older responses read false.
const favorited = z.boolean().optional().catch(false);
// 트랙·세션 시간표 (contract "트랙·세션 시간표 — 2026-10-26").
const trackSchema = z.object({ id: z.number().int(), name: text, room: text.nullable(), position: z.number().int() });
export type Track = z.infer<typeof trackSchema>;
const sessionSchema = z.object({
  id: z.number().int(),
  // null = 모든 트랙에 걸친 전폭 세션(키노트·쉬는시간·점심).
  trackId: z.number().int().nullable(),
  kind: z.enum(["TALK", "KEYNOTE", "BREAK", "MEAL", "OTHER"]).catch("OTHER"),
  title: text,
  startAt: text,
  endAt: text,
  speakers: z.array(z.object({ name: text, affiliation: text.nullable() })),
  tags: z.array(tagSchema).catch([]),
  favorited,
});
export type Session = z.infer<typeof sessionSchema>;
export const timetableSchema = z.object({
  activityId: z.number().int(),
  schedule: scheduleSchema,
  tracks: z.array(trackSchema),
  sessions: z.array(sessionSchema),
});
export type Timetable = z.infer<typeof timetableSchema>;
// 내 활동 (contract "내 활동 서버 저장·일정 상태", web since 2026-10-26 "웹 로그인" 2단계 B): `GET /v1/me/activities` items. A mark on a
// schedule is the admin status by id or a default one by kind (`statusId` null).
export const myActivitySchema = z.object({
  activityId: z.number().int().positive(),
  createdAt: text,
  deleted: z.boolean(),
  statuses: z.array(z.object({ scheduleId: z.number().int(), statusId: z.number().int().nullable(), kind: z.enum(STATUS_KINDS) })),
});
export type MyActivity = z.infer<typeof myActivitySchema>;
export const myActivityListSchema = z.object({ items: z.array(myActivitySchema) });
/** `PUT /v1/me/activities/{id}/schedules/{scheduleId}` body: an admin status by id or a default one by kind, never both. */
export const statusPickSchema = z.union([
  z.object({ statusId: z.number().int().positive() }).strict(),
  z.object({ kind: z.enum(STATUS_KINDS) }).strict(),
]);
export type StatusPick = z.infer<typeof statusPickSchema>;
// 찜한 세션 (contract "트랙·세션 시간표" › 공개 API `GET /v1/me/session-favorites`): the web reads them only for the 오늘 내 행사 card.
export const favoriteSessionSchema = z.object({
  activityId: z.number().int(),
  activityName: text,
  scheduleId: z.number().int(),
  scheduleTitle: text,
  /** The parent block (start·end·zone), sent since 2026-10-26 (#401): 내 시간표 draws from it without reading the timetable again. */
  schedule: scheduleSchema,
  /** The session's track; null for a full-width session. */
  track: trackSchema.nullable().optional().catch(null),
  session: sessionSchema,
  favoritedAt: text,
});
export type FavoriteSession = z.infer<typeof favoriteSessionSchema>;
export const favoriteSessionListSchema = z.object({ items: z.array(favoriteSessionSchema) });
/** `HH:mm` (24h) of an instant in a zone, for timetable rows. */
export function clockTime(time: number, timeZone: string): string {
  return koreanParts(time, timeZone, true).time;
}
/** `M월 d일 (요일)` of an instant in a zone, with its year when outside the current Seoul year (the `scheduleLabel` rule). */
export function dayLabel(time: number, timeZone: string, now = Date.now()): string {
  const parts = koreanParts(time, timeZone, false);
  return `${parts.year === koreanParts(now, "Asia/Seoul", false).year ? "" : `${parts.year}년 `}${parts.day}`;
}
/**
 * 하루 시간표 (2026-10-26 사용자 "10/28일 누르면 시간표보기 일정추가 이런식으로"): the timed blocks of one Seoul day, folded into
 * one day row on the activity detail and shown together in the day timetable. `day` is the Seoul `yyyy-MM-dd`, `start`/`end`
 * the earliest start and latest end, `timeZone` the first block's (they share one in practice).
 */
export type ScheduleDay = { day: string; label: string; schedules: Schedule[]; start: number; end: number; timeZone: string };
/**
 * Detail schedules with same-day timed blocks folded: a block with both `startAt` and `endAt` on one Seoul day joins that day's
 * group, which replaces the first such block in server order; a day with a single block stays a plain row, as do all-day,
 * one-bound, multi-day and undated blocks. Data is untouched — this is display grouping only.
 */
export function foldScheduleDays(schedules: Schedule[], now = Date.now()): (Schedule | ScheduleDay)[] {
  const days = new Map<string, ScheduleDay>();
  const items: (Schedule | ScheduleDay)[] = [];
  for (const schedule of schedules) {
    const start = parseTime(schedule.startAt);
    const end = parseTime(schedule.endAt);
    const day = start === undefined || end === undefined ? undefined : calendarDay(start, "Asia/Seoul");
    if (day === undefined || day !== calendarDay(end!, "Asia/Seoul")) {
      items.push(schedule);
      continue;
    }
    const group = days.get(day);
    if (group) {
      group.schedules.push(schedule);
      group.start = Math.min(group.start, start!);
      group.end = Math.max(group.end, end!);
      continue;
    }
    const timeZone = schedule.timeZone ?? "Asia/Seoul";
    const made: ScheduleDay = { day, label: dayLabel(start!, timeZone, now), schedules: [schedule], start: start!, end: end!, timeZone };
    days.set(day, made);
    items.push(made);
  }
  return items.map((item) => ("schedules" in item && item.schedules.length === 1 ? item.schedules[0] : item));
}
/** `10:00 ~ 17:30 · 블록 4개`: the day row's second line and the day timetable's subtitle. */
export function scheduleDayRange(day: ScheduleDay): string {
  return `${clockTime(day.start, day.timeZone)} ~ ${clockTime(day.end, day.timeZone)} · 블록 ${day.schedules.length}개`;
}
const placeSchema = z.object({ name: text, address: text, lat: z.number(), lng: z.number() });
export type Place = z.infer<typeof placeSchema>;
export const activitySchema = z.object({
  id: z.number().int().positive(),
  name: text,
  description: text,
  organization: named,
  // 활동 상세의 프로그램도 가장 가까운 로고를 싣는다(contract "조직 로고와 로고 계승"); older responses omit it.
  program: named.extend({ logoUrl: text.nullable().optional().catch(null) }),
  officialUrl: text,
  // 신청 링크(contract "신청 확인 조건 완화·공개 applicationUrl"); older responses omit it.
  applicationUrl: text.nullable().optional().catch(null),
  imageUrl: text.nullable(),
  application: scheduleSchema.nullable(),
  activitySchedule: scheduleSchema,
  schedules: z.array(scheduleSchema),
  // 활동 종류 code (contract "활동 종류"); older responses omit it.
  category: text.nullable().optional().catch(null),
  /** 분야+성격, kept for older apps; read `fieldTags`/`traitTags` instead (contract "분야·성격 태그와 아이콘"). */
  tags,
  fieldTags: splitTags,
  traitTags: splitTags,
  /** Every 분야 tag in the same order, for the detail page. */
  fieldTagsAll: splitTags,
  // A malformed eligibility must not break the activity.
  eligibility: eligibilitySchema.nullable().optional().catch(null),
  // 활동 장소 (contract "활동 장소 — 2026-10-19"): the collected phrase and the curated place; older responses omit both.
  location: text.nullable().optional().catch(null),
  place: placeSchema.nullable().optional().catch(null),
  // 활동 상태 (contract "활동 상태" 2026-10-26); older responses omit it, an unknown value reads as none.
  status: z.enum(ACTIVITY_STATUSES).optional().catch(undefined),
  favorited,
  // 시간표 배지 (contract "트랙·세션 시간표"): any block with a timetable; older responses omit it (the blocks' own flags stand in).
  hasTimetable: z.boolean().optional().catch(undefined),
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
  /** 프로그램 기본 종류 code (contract "활동 종류"), null when unset; absent on older responses. */
  category: text.nullable().optional().catch(null),
  openActivityCount: z.number().int().nonnegative(),
  nearestRecruitmentEndAt: text.nullable(),
  /** `nearestRecruitmentEndAt` is an activity start (no recruitment deadline), shown as `행사`; absent on older responses. */
  nearestIsActivityStart: z.boolean().optional().catch(false),
  matchedActivityIds: z.array(z.number().int()),
  /** Latest activity start among the program's published activities; absent on older responses (contract "마감 숨기기"). */
  latestActivityAt: text.nullable().optional().catch(null),
  /** 모집 예정 activities and the earliest of their recruitment (else activity) starts; absent on older responses (contract "마감 숨기기", 2026-10-19 밤). */
  upcomingActivityCount: z.number().int().nonnegative().optional().catch(0),
  nearestRecruitmentStartAt: text.nullable().optional().catch(null),
  /** Program tags, else the union of its open activities' tags (contract "태그"); kept for older apps. */
  tags,
  fieldTags: splitTags,
  traitTags: splitTags,
  /** `/v1/programs/{id}/logo?v=…` on the API origin, null without a logo; absent on older responses (contract "프로그램 로고", 2026-10-19 밤). */
  logoUrl: text.nullable().optional().catch(null),
  /** Activities per 활동 상태 in the query's scope, zeros left out (contract "활동 상태"); absent on older responses. */
  statusCounts: z.record(z.string(), z.number().int().nonnegative()).optional().catch(undefined),
  favorited,
});
export const programPageSchema = z.object({
  items: z.array(programSummarySchema),
  nextCursor: z.number().int().nullable(),
});
export type ProgramSummary = z.infer<typeof programSummarySchema>;
export type ProgramPage = z.infer<typeof programPageSchema>;
/**
 * `GET /v1/me/favorites` (contract "즐겨찾기(하트)"): programs most recently favorited first, activities by nearest deadline (past ones
 * last) — shown in server order. `notifyLevel` and the apps' push settings are not read on the web.
 */
export const favoritesSchema = z.object({
  programs: z.array(z.object({ program: programSummarySchema, favoritedAt: text, newRecruitment: z.boolean().catch(false) })),
  activities: z.array(z.object({ activity: activitySchema, program: programSummarySchema, favoritedAt: text })),
});
export type Favorites = z.infer<typeof favoritesSchema>;
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
/** The status to show: the server's, else (an older server) one read from the recruitment window alone. */
export function activityStatus(activity: Activity, now = Date.now()): ActivityStatus {
  if (activity.status) return activity.status;
  const { open, label } = recruitment(activity.application, now);
  return open ? "RECRUITING" : label === "모집 예정" ? "RECRUITING_UPCOMING" : "RECRUITMENT_CLOSED";
}
/** Recruitment state comes only from the application period; a missing bound is open on that side. */
export function recruitment(
  application: Schedule | null,
  now = Date.now(),
): { open: boolean; label: string } {
  // 모집 일정이 없는 활동은 종류와 상관없이 마감 (user 2026-10-26, contract "모집 일정 없는 활동은 마감").
  if (!application) return { open: false, label: "모집 마감" };
  // Date-only windows compare Seoul days: before the start → 예정, before the exclusive end → 중, otherwise 마감
  // (contract "열린 활동 판정 일원화"). Timed windows compare instants; a missing end or start is open on that side.
  if (!application.startAt && !application.endAt) {
    if (!application.startDate && !application.endDateExclusive) return { open: false, label: "모집 마감" };
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
/**
 * Open Graph recruitment phrase (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): 모집 중 `M월 d일 마감`, 모집 예정
 * `M월 d일 모집 시작`, otherwise `모집 마감`. A date-only end is exclusive, so the last day is one day earlier.
 */
export function recruitmentPhrase(application: Schedule | null, now = Date.now()): string {
  const { open, label } = recruitment(application, now);
  if (!application || (!open && label !== "모집 예정")) return "모집 마감";
  const lastDay = parseTime(application.endDateExclusive);
  const day = open
    ? monthDay(application.endAt ?? (lastDay === undefined ? null : new Date(lastDay - DAY).toISOString()))
    : monthDay(application.startAt ?? application.startDate);
  return day ? `${day} ${open ? "마감" : "모집 시작"}` : label;
}
/** `yyyy-MM-dd` of an instant in Asia/Seoul, comparable with the API's date-only fields. */
export function seoulDay(now: number): string {
  return calendarDay(now, "Asia/Seoul");
}
/** `ko-KR` parts of an instant in a zone, with `HH:mm` (24h) when `withTime`. */
function koreanParts(time: number, timeZone: string, withTime: boolean) {
  const parts = new Intl.DateTimeFormat("ko-KR", {
    timeZone,
    year: "numeric",
    month: "numeric",
    day: "numeric",
    weekday: "short",
    ...(withTime ? { hour: "2-digit", minute: "2-digit", hourCycle: "h23" } : {}),
  }).formatToParts(new Date(time));
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? "";
  return {
    year: part("year"),
    day: `${part("month")}월 ${part("day")}일 (${part("weekday")})`,
    time: withTime ? `${part("hour")}:${part("minute")}` : "",
  };
}
export const DAY = 86_400_000;
/** Which slot a schedule fills: the end-only suffix is `마감` for recruitment and `까지` for activity periods and detail schedules. */
export type ScheduleKind = "recruitment" | "activity";
/**
 * Readable schedule text (contract "일정 표시 우선순위 — 2026-10-19 밤"): curated structured dates win over the collected
 * `dateLabel`. Timed → `M월 d일 (요일) HH:mm` in the schedule's zone (range `A ~ B`, same day `A ~ HH:mm`, end only
 * `~ B 마감`/`~ B까지` by `kind`); all-day → `M월 d일 (요일)` with the inclusive end (`endDateExclusive` minus a day); a bound
 * outside the current Seoul year carries its year. `dateLabel` shows only without any structured date; else empty.
 */
export function scheduleLabel(schedule: Schedule | null, kind: ScheduleKind, now = Date.now()): string {
  if (!schedule) return "";
  const startAt = parseTime(schedule.startAt);
  const endAt = parseTime(schedule.endAt);
  const timed = startAt !== undefined || endAt !== undefined;
  const endDateExclusive = parseTime(schedule.endDateExclusive);
  const [start, end] = timed
    ? [startAt, endAt]
    : [parseTime(schedule.startDate), endDateExclusive === undefined ? undefined : endDateExclusive - DAY];
  if (start === undefined && end === undefined) return schedule.dateLabel;
  const timeZone = timed ? (schedule.timeZone ?? "Asia/Seoul") : "UTC";
  const from = start === undefined ? undefined : koreanParts(start, timeZone, timed);
  const to = end === undefined ? undefined : koreanParts(end, timeZone, timed);
  const thisYear = koreanParts(now, "Asia/Seoul", false).year;
  const text = (p: { year: string; day: string; time: string }) =>
    `${p.year === thisYear ? "" : `${p.year}년 `}${p.day}${p.time && ` ${p.time}`}`;
  if (!from) return `~ ${text(to!)}${kind === "recruitment" ? " 마감" : "까지"}`;
  if (!to || end === start) return text(from);
  if (timed && from.year === to.year && from.day === to.day) return `${text(from)} ~ ${to.time}`;
  return `${text(from)} ~ ${text(to)}`;
}
/**
 * 모집 날짜 표기 (contract "모집 날짜 표기 — 2026-10-26"): with both bounds of the recruitment window, the period as `M.d – M.d` (no
 * times; one day alone when both fall on it; a bound outside the current Seoul year as `yyyy.M.d`) and, while 모집 중, a `D-n`/`D-DAY`
 * badge counted in Seoul days to the last day (a date-only end is exclusive). Otherwise the `scheduleLabel` text without a badge
 * (an end alone stays `~ M월 d일 (요일) 마감`).
 */
export function recruitmentDate(application: Schedule | null, now = Date.now()): { text: string; dday?: string } {
  if (!application) return { text: "" };
  const startAt = parseTime(application.startAt);
  const endAt = parseTime(application.endAt);
  const timed = startAt !== undefined || endAt !== undefined;
  const endDateExclusive = parseTime(application.endDateExclusive);
  const [start, last] = timed
    ? [startAt, endAt]
    : [parseTime(application.startDate), endDateExclusive === undefined ? undefined : endDateExclusive - DAY];
  if (start === undefined || last === undefined || last < start)
    return { text: scheduleLabel(application, "recruitment", now) };
  const timeZone = timed ? (application.timeZone ?? "Asia/Seoul") : "UTC";
  const thisYear = seoulDay(now).slice(0, 4);
  const short = (time: number) => {
    const [year, month, day] = calendarDay(time, timeZone).split("-");
    return `${year === thisYear ? "" : `${year}.`}${Number(month)}.${Number(day)}`;
  };
  const text = short(start) === short(last) ? short(start) : `${short(start)} – ${short(last)}`;
  if (!recruitment(application, now).open) return { text };
  const days = Math.round((Date.parse(calendarDay(last, timeZone)) - Date.parse(seoulDay(now))) / DAY);
  return { text, dday: days <= 0 ? "D-DAY" : `D-${days}` };
}
/**
 * 모집 시작 D-day (contract "모집 예정 시작 D-day — 2026-10-26"): while 모집 예정 with a known recruitment start (`startAt`, or `startDate`
 * of a date-only window), `시작 D-n` in Seoul days to the start day, `오늘 시작` on that day; undefined otherwise (the badge stays `모집 예정`).
 */
export function upcomingStartDday(activity: Activity, now = Date.now()): string | undefined {
  const application = activity.application;
  if (!application || activityStatus(activity, now) !== "RECRUITING_UPCOMING") return undefined;
  const startAt = parseTime(application.startAt);
  const startDay = startAt === undefined ? application.startDate : calendarDay(startAt, "Asia/Seoul");
  if (!startDay) return undefined;
  const days = Math.round((Date.parse(startDay) - Date.parse(seoulDay(now))) / DAY);
  return days <= 0 ? "오늘 시작" : `시작 D-${days}`;
}
/** `yyyy-MM-dd` of an instant in a zone. */
export function calendarDay(time: number, timeZone: string): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(time));
}
export function parseTime(value: string | null): number | undefined {
  const time = value ? Date.parse(value) : NaN;
  return Number.isNaN(time) ? undefined : time;
}
/**
 * Same-origin `<img src>` for a program logo (contract "프로그램 로고"): the API's `/v1/programs/{id}/logo?v=…` becomes the
 * proxy path `/api/programs/{id}/logo?v=…`; anything else (null, older shape, foreign origin) yields undefined → initial avatar.
 */
export function programLogoSrc(logoUrl: string | null | undefined): string | undefined {
  const match = logoUrl?.match(/^\/v1\/(programs\/[1-9]\d{0,17}\/logo\?v=\d{1,18})$/);
  return match ? `/api/${match[1]}` : undefined;
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
/** GitHub·Instagram·Behance·카카오톡 take a username only (contract "연락처 아이콘·바로가기" 2026-10-26, same table as the apps and the API). */
const USERNAME_RULES: Partial<Record<Card["contacts"][number]["kind"], { host?: string; max: number }>> = {
  github: { host: "github.com", max: 39 },
  instagram: { host: "instagram.com", max: 30 },
  behance: { host: "behance.net", max: 64 },
  kakao: { max: 20 },
};

/**
 * Same-origin `<img src>` for a card photo a signed-in person may see (own card, received card): the API's
 * `/v1/cards/:id/photo?v=…` becomes `/api/cards/:id/photo?v=…` (Bearer added by the proxy); anything else → undefined → initial avatar.
 */
export function cardPhotoSrc(photoUrl: string | null | undefined): string | undefined {
  const match = photoUrl?.match(/^\/v1\/(cards\/[0-9a-f-]{36}\/photo\?v=\d{1,18})$/i);
  return match ? `/api/${match[1]}` : undefined;
}
/** The username in a contact value: a profile URL saved before 2026-10-26 gives its first path part, `@` and spaces are dropped. */
export function contactUsername(contact: Card["contacts"][number]): string | undefined {
  const rule = USERNAME_RULES[contact.kind];
  if (!rule) return;
  let text = contact.value.trim();
  const prefix = rule.host && new RegExp(`^(https?://)?((www|m)\\.)?${rule.host.replace(".", "\\.")}/`, "i").exec(text);
  if (prefix) text = text.slice(prefix[0].length).split(/[/?#]/)[0];
  const id = text.replace(/[\s@]/g, "");
  return id && id.length <= rule.max && /^[A-Za-z0-9._-]+$/.test(id) ? id : undefined;
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
  const host = USERNAME_RULES[contact.kind]?.host;
  if (host) {
    const id = contactUsername(contact);
    return id && `https://${contact.kind === "behance" ? "www." : ""}${host}/${id}`;
  }
  // 카카오톡 ID has no public link; an open-chat URL saved before 2026-10-26 still opens.
  if (contact.kind === "kakao") return safeWebUrl(contact.value);
}
// 개인정보 동의 상태 (contract "개인정보 동의"): the part of `GET /v1/profile` the web needs; the profile itself is not shown on the web yet.
// `version` 0 means nothing saved since sign-up (가입 취소 applies); a non-null `consent` means an earlier version was agreed (재동의).
export const profileConsentSchema = z.object({
  version: z.number().int().nonnegative(),
  consent: z.object({ privacyVersion: z.string(), termsVersion: z.string(), consentedAt: z.string() }).nullable(),
  consentRequired: z.boolean(),
});
export type ProfileConsent = z.infer<typeof profileConsentSchema>;
