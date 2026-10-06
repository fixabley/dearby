import { z } from "zod";

export const uuid = z.uuid();
const text = z.string();
const contact = z.object({
  id: uuid,
  kind: z.enum(["phone", "email", "kakao", "instagram", "github", "behance"]),
  label: text,
  value: text,
});
const history = z.object({
  id: uuid,
  title: text,
  role: text,
  startDate: text,
  endDate: text.nullable(),
  description: text,
});
// Only the public card projection is accepted. Unknown fields are stripped at the proxy.
export const cardSchema = z.object({
  id: uuid,
  name: text,
  description: text,
  profileName: text,
  job: text,
  introduction: text,
  contacts: z.array(contact),
  histories: z.array(history),
  createdAt: text,
});
export type Card = z.infer<typeof cardSchema>;
// Activity copies chosen by the sender; not proof of participation.
const sharedActivity = z.object({ id: uuid, title: text });
export const sharePageSchema = z.object({
  share: z.object({
    id: uuid,
    cardId: uuid,
    activities: z.array(sharedActivity).max(10),
    createdAt: text,
  }),
  card: cardSchema,
});
export type SharePage = z.infer<typeof sharePageSchema>;
export const guestShareSchema = z.object({
  cardId: uuid,
  shareId: uuid,
  activities: z.array(sharedActivity).max(10),
  savedAt: text,
});
export type GuestShare = z.infer<typeof guestShareSchema>;
export const activitySchema = z.object({
  id: uuid,
  programId: uuid,
  organizationId: uuid,
  title: text,
  summary: text,
  participationType: z.enum(["registration", "selection"]),
  recruitmentStatus: z.enum(["open", "scheduled", "closed", "unknown"]),
  isRecruiting: z.boolean(),
  recruitmentStartAt: text.nullable(),
  recruitmentEndAt: text.nullable(),
  dateLabel: text,
  location: text.nullable(),
  cost: text.nullable(),
  audience: text.nullable(),
  qualification: text.nullable(),
  roles: z.array(text),
  schedules: z.array(
    z.object({
      id: uuid,
      title: text,
      startAt: text.nullable(),
      endAt: text.nullable(),
      dateLabel: text,
      timeZone: text,
    }),
  ),
  officialUrl: text,
  applicationUrl: text.nullable(),
  sourceCheckedAt: text.nullable(),
  validUntil: text.nullable(),
  freshness: z.enum(["verified", "stale", "unavailable"]),
  sourceNote: text,
});
export const catalogSchema = z.object({
  generatedAt: text,
  organizations: z.array(z.object({ id: uuid, name: text, description: text })),
  programs: z.array(
    z.object({
      id: uuid,
      organizationId: uuid,
      title: text,
      description: text,
    }),
  ),
  activities: z.array(activitySchema),
});
export type Activity = z.infer<typeof activitySchema>;
export type Catalog = z.infer<typeof catalogSchema>;
export function isRecruiting(activity: Activity, now = Date.now()): boolean {
  return (
    activity.isRecruiting &&
    activity.recruitmentStatus === "open" &&
    activity.freshness === "verified" &&
    !!activity.sourceCheckedAt &&
    Date.parse(activity.sourceCheckedAt) <= now &&
    !!activity.validUntil &&
    now < Date.parse(activity.validUntil) &&
    (!activity.recruitmentStartAt ||
      Date.parse(activity.recruitmentStartAt) <= now) &&
    (!activity.recruitmentEndAt || now < Date.parse(activity.recruitmentEndAt))
  );
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
