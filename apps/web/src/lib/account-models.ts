// Signed-in account DTOs (contract native-v1.md "프로필과 버전 이력", "명함·지갑", "명함 교환", "알림 목록", "연락처 인증",
// "추천 설정 퍼널·지원 자격 정보"): what the Bearer branch of the `/api/*` proxy accepts from the browser and lets through from
// the API. Public shapes (Card, Contact, History, ProgramSummary) stay in `models.ts`; this file only adds the account-only ones.
import { z } from "zod";
import { CONTACT_KINDS, cardSchema, contactSchema, exchangeContextSchema, historySchema, programSummarySchema, uuid } from "./models";

const text = z.string();
const positiveInt = z.number().int().positive();
const dateText = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
/** QR code / guest token shape (43 base64url characters). */
const CODE = /^[A-Za-z0-9_-]{43}$/;
const ref = z.object({ id: z.number().int(), name: text }).nullable();

// ── 프로필 ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const APPROVALS = ["approved", "pending", "rejected"] as const;
export const profileJobSchema = z.object({
  id: uuid,
  job: ref,
  jobName: text.nullable(),
  approval: z.enum(APPROVALS).catch("pending"),
  approvalReason: text.nullable(),
});
export type ProfileJob = z.infer<typeof profileJobSchema>;
export const evidenceSchema = z.object({ id: uuid, fileName: text, contentType: text, size: z.number().int().nonnegative(), uploadedAt: text });
export type Evidence = z.infer<typeof evidenceSchema>;
/** A history on my profile carries its 인증자료 list; a card's history does not (contract "인증자료"). */
export const profileHistorySchema = historySchema.extend({ evidence: z.array(evidenceSchema).optional().catch([]) });
export type ProfileHistory = z.infer<typeof profileHistorySchema>;
export const consentSchema = z.object({ privacyVersion: text, termsVersion: text, consentedAt: text }).nullable();
export const profileSchema = z.object({
  id: uuid,
  name: text,
  jobs: z.array(profileJobSchema),
  /** 승인된 직무 이름을 ` · `로 이은 읽기 전용 값(명함의 `job`과 같다). */
  job: text,
  introLink: text.nullable().optional().catch(null),
  contacts: z.array(contactSchema),
  histories: z.array(profileHistorySchema),
  /** `If-Match` of the next PUT; 0 = never saved. */
  version: z.number().int().nonnegative(),
  updatedAt: text,
  consent: consentSchema.optional().catch(null),
  consentRequired: z.boolean().optional().catch(false),
  /** `/v1/profile/photo?v=<ms>` or null (contract "프로필 사진"); shown through `/api/profile/photo?v=`. */
  photoUrl: text.nullable().optional().catch(null),
});
export type Profile = z.infer<typeof profileSchema>;
export const photoSchema = z.object({ photoUrl: text });
/** Same-origin `<img src>` of my profile photo, or undefined without one. */
export function profilePhotoSrc(photoUrl: string | null | undefined): string | undefined {
  const match = photoUrl?.match(/^\/v1\/(profile\/photo\?v=\d{1,18})$/);
  return match ? `/api/${match[1]}` : undefined;
}

/** `PUT /v1/profile` body (contract "프로필과 버전 이력"); the API validates values, the proxy only the shape. */
export const profileRequestSchema = z
  .object({
    name: z.string().trim().min(1).max(100),
    jobs: z
      .array(z.object({ id: uuid, jobId: positiveInt.optional(), jobName: z.string().trim().min(1).max(100).optional() }).strict())
      .max(10),
    introLink: z.string().trim().max(500).nullable().optional(),
    contacts: z
      .array(z.object({ id: uuid, kind: z.enum(CONTACT_KINDS), label: z.string().max(60), value: z.string().trim().min(1).max(500) }).strict())
      .max(50),
    histories: z
      .array(
        z
          .object({
            id: uuid,
            kind: z.enum(["organization", "activity"]),
            organizationId: positiveInt.optional(),
            organizationName: z.string().trim().min(1).max(200).optional(),
            programId: positiveInt.optional(),
            programName: z.string().trim().min(1).max(200).optional(),
            activityId: positiveInt.optional(),
            activityName: z.string().trim().min(1).max(200).optional(),
            roleId: positiveInt.optional(),
            roleName: z.string().trim().min(1).max(100).optional(),
            startDate: dateText,
            endDate: dateText.nullable().optional(),
            description: z.string().max(2000),
          })
          .strict(),
      )
      .max(100),
  })
  .strict();
export type ProfileRequest = z.infer<typeof profileRequestSchema>;

// 이력 입력의 선택 창 검색 (`GET /v1/organizations|programs|roles|jobs`, contract "이력"·"직무").
const named = z.object({ id: z.number().int(), name: text });
export const activityInfoPageSchema = z.object({
  items: z.array(
    z.object({
      id: z.number().int(),
      name: text,
      nameEn: text.nullable().optional().catch(null),
      parentOrganizationId: z.number().int().nullable().optional().catch(null),
      organization: named.nullable().optional().catch(null),
      parent: named.nullable().optional().catch(null),
    }),
  ),
  nextCursor: z.number().int().nullable(),
});
export type ActivityInfoPage = z.infer<typeof activityInfoPageSchema>;
export type ActivityInfoItem = ActivityInfoPage["items"][number];

// ── 연락처 인증 (contract "연락처 인증") ───────────────────────────────────────────────────────────────────────────────────────
export const githubStartBodySchema = z.object({ platform: z.literal("web") }).strict();
export const githubStartSchema = z.object({ authorizeUrl: z.string().url().startsWith("https://github.com/") });
export const githubCompleteBodySchema = z.object({ code: uuid }).strict();
export const emailChallengeBodySchema = z.object({ email: z.string().trim().min(3).max(254) }).strict();
export const emailChallengeSchema = z.object({ challengeId: uuid, expiresAt: text });
export const emailVerifyBodySchema = z.object({ challengeId: uuid, code: z.string().regex(/^\d{6}$/) }).strict();

// ── 추천 설정·지원 자격 (contract "추천 설정 퍼널·지원 자격 정보·추천"; the web only shows them) ──────────────────────────────
export const recommendationPreferencesSchema = z.object({
  fieldTagIds: z.array(z.number().int()),
  categories: z.array(text),
  traitTagIds: z.array(z.number().int()),
  completedAt: text.nullable(),
});
export type RecommendationPreferences = z.infer<typeof recommendationPreferencesSchema>;
export const eligibilityProfileSchema = z.object({
  consent: z.object({ version: text, agreedAt: text }).nullable(),
  educationStatus: text.nullable(),
  employment: text.nullable(),
  schoolYear: z.number().int().nullable(),
  birthYear: z.number().int().nullable(),
});
export type EligibilityProfile = z.infer<typeof eligibilityProfileSchema>;

// ── 내 명함 (contract "명함·지갑", "명함 QR 코드") ──────────────────────────────────────────────────────────────────────────────
/** My card from `GET /v1/cards`·`POST`·`PUT`: the public card shape plus `updatedAt` (the API's `ownerId`·`template` are dropped). */
export const myCardSchema = cardSchema.extend({ updatedAt: text.optional().catch(undefined) });
export type MyCard = z.infer<typeof myCardSchema>;
export const myCardListSchema = z.object({ items: z.array(myCardSchema) });
export const cardPublishSchema = z
  .object({
    name: z.string().trim().min(1).max(60),
    description: z.string().max(200),
    contactIds: z.array(uuid).max(50),
    historyIds: z.array(uuid).max(100),
    includePhoto: z.boolean().optional(),
    includeIntroLink: z.boolean().optional(),
  })
  .strict();
export type CardPublish = z.infer<typeof cardPublishSchema>;
/** `POST /v1/cards/:id/qr-codes` 201: the only place the code text appears; the QR page keeps it in memory. */
export const qrCodeSchema = z.object({ id: uuid, code: z.string().regex(CODE), expiresAt: text });
export type QrCode = z.infer<typeof qrCodeSchema>;

// ── 받은 명함 (contract "명함 교환") ────────────────────────────────────────────────────────────────────────────────────────────
export const receiptSchema = z.object({
  id: uuid,
  card: cardSchema,
  context: exchangeContextSchema,
  receivedAt: text,
  reciprocal: z.boolean(),
  /** 회수 예정 시각(단방향만); 맞교환이면 null. */
  expiresAt: text.nullable(),
});
export type Receipt = z.infer<typeof receiptSchema>;
export const walletSchema = z.object({ items: z.array(receiptSchema) });
export const walletSaveBodySchema = z.object({ activityId: positiveInt.optional(), code: z.string().regex(CODE).optional() }).strict();
export const reciprocateBodySchema = z.object({ myCardId: uuid }).strict();
export const reciprocateSchema = z.object({ reciprocal: z.boolean() });
export const blockBodySchema = z.object({ cardId: uuid }).strict();
export const blockSchema = z.object({ id: uuid, name: text, blockedAt: text });

// ── 알림 (contract "알림 목록(발견 우상단 종)") ────────────────────────────────────────────────────────────────────────────────
export const NOTIFICATION_KIND_LABEL: Record<string, string> = {
  NEW: "새 모집",
  APPLICATION_START: "모집 시작",
  DEADLINE_EVE: "마감 D-1",
  DEADLINE: "오늘 마감",
  ANNOUNCEMENT: "발표",
  INTERVIEW: "면접",
  EVENT: "행사",
  ACTIVITY_START: "활동 시작",
};
export const notificationItemSchema = z.object({
  id: z.number().int(),
  kind: text,
  activity: z.object({ id: z.number().int(), name: text }),
  createdAt: text,
  readAt: text.nullable(),
});
export type NotificationItem = z.infer<typeof notificationItemSchema>;
export const notificationGroupSchema = z.object({ program: programSummarySchema, unread: z.number().int().nonnegative(), items: z.array(notificationItemSchema) });
export type NotificationGroup = z.infer<typeof notificationGroupSchema>;
export const notificationPageSchema = z.object({ groups: z.array(notificationGroupSchema), nextCursor: z.number().int().nullable() });
export type NotificationPage = z.infer<typeof notificationPageSchema>;
export const unreadCountSchema = z.object({ count: z.number().int().nonnegative() });

// ── 내 활동 (contract "내 활동 서버 저장"; the QR activity chip reads it) ──────────────────────────────────────────────────────
export const userActivitiesSchema = z.object({
  items: z.array(
    z.object({
      activityId: z.number().int(),
      createdAt: text,
      deleted: z.boolean(),
      statuses: z
        .array(z.object({ scheduleId: z.number().int(), statusId: z.number().int().nullable().optional(), kind: text.nullable().optional() }))
        .catch([]),
    }),
  ),
});
export type UserActivities = z.infer<typeof userActivitiesSchema>;
