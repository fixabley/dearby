import { z } from 'zod';
export const id = z.uuid();
const text = (max: number) => z.string().max(max);
const date = z.iso.date();
const contact = z.strictObject({
  id, kind: z.enum(['phone', 'email', 'kakao', 'instagram', 'github', 'behance']),
  label: text(100), value: z.string().trim().min(1).max(1000).refine(v => ![...v].some(c => c.charCodeAt(0) < 32 || c.charCodeAt(0) === 127), 'Invalid contact characters'),
}).refine(c => !/^[a-z][a-z\d+.-]*:/i.test(c.value) || /^https:\/\//i.test(c.value), 'Only HTTPS contact URLs are allowed');
const history = z.strictObject({ id, title: text(200), role: text(200), startDate: date,
  endDate: date.nullable(), description: text(5000),
}).refine(h => h.endDate === null || h.endDate >= h.startDate, 'Invalid date range');
export const profileInput = z.strictObject({ name: text(100), job: text(200), introduction: text(5000),
  contacts: z.array(contact).max(100), histories: z.array(history).max(100),
}).refine(p => new Set(p.contacts.map(c => c.id)).size === p.contacts.length && new Set(p.histories.map(h => h.id)).size === p.histories.length, 'Duplicate IDs');
export const profileSchema = profileInput.safeExtend({id,updatedAt:z.iso.datetime()});
export type Profile = z.infer<typeof profileSchema>;
export const cardSchema = z.strictObject({...profileInput.shape,id,ownerId:id,name:z.string().min(1).max(100),description:text(2000),profileName:text(100),createdAt:z.iso.datetime()});
export type Card = z.infer<typeof cardSchema>;
export const context = z.strictObject({ activityId: id.nullable(), label: z.string().trim().min(1).max(200).nullable() })
  .refine(c => c.activityId === null || c.label === null, 'Choose activity or label');
export const cardInput = z.strictObject({ name: z.string().trim().min(1).max(100), description: text(2000),
  contactIds: z.array(id).max(100), historyIds: z.array(id).max(100),
}).refine(c => new Set(c.contactIds).size === c.contactIds.length && new Set(c.historyIds).size === c.historyIds.length, 'Duplicate IDs');
export const importItem = z.strictObject({cardId: id, context, savedAt: z.iso.datetime()});
export const exchangeInput = z.strictObject({ cardId: id, recipientProfileId: id, context, requestId: id });
export class ApiError extends Error {
  constructor(public statusCode: number, public code: string, message: string) { super(message); }
}
export const missing = () => new ApiError(404, 'NOT_FOUND', 'Resource not found');

// Shared with documentation; route handlers still own Zod parsing (not Fastify schema validation).
export const challengeInput = z.strictObject({email:z.email().max(254).transform(e=>e.trim().toLowerCase())});
export const sessionInput = z.strictObject({challengeId:id,code:z.string().regex(/^\d{6}$/)});
export const importEnvelope = z.strictObject({items:z.array(z.unknown()).max(100)});
