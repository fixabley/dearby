import { createHash } from 'node:crypto';
import { z } from 'zod';
import type { DB } from './database.js';

const time = z.iso.datetime({offset:true});
const url = z.url({protocol:/^https?$/});
export const organizationSchema = z.strictObject({id:z.uuid(), name:z.string(), description:z.string()});
export const programSchema = z.strictObject({id:z.uuid(), organizationId:z.uuid(), title:z.string(), description:z.string()});
export const scheduleSchema = z.strictObject({id:z.uuid(), title:z.string(), startAt:time.nullable(), endAt:time.nullable(), dateLabel:z.string(), timeZone:z.string()});
export const activitySchema = z.strictObject({
  id:z.uuid(), programId:z.uuid(), organizationId:z.uuid(), title:z.string(), summary:z.string(),
  participationType:z.enum(['registration','selection']), recruitmentStatus:z.enum(['open','scheduled','closed','unknown']),
  isRecruiting:z.boolean(), recruitmentStartAt:time.nullable(), recruitmentEndAt:time.nullable(),
  dateLabel:z.string(), location:z.string().nullable(), cost:z.string().nullable(), audience:z.string().nullable(), qualification:z.string().nullable(),
  roles:z.array(z.string()), schedules:z.array(scheduleSchema), officialUrl:url, applicationUrl:url.nullable(),
  sourceCheckedAt:time.nullable(), validUntil:time.nullable(), freshness:z.enum(['verified','stale','unavailable']), sourceNote:z.string(),
});
export const catalogSchema = z.strictObject({generatedAt:time, organizations:z.array(organizationSchema), programs:z.array(programSchema), activities:z.array(activitySchema)});
export type Activity = z.infer<typeof activitySchema>;
export type Organization = z.infer<typeof organizationSchema>;
export type Program = z.infer<typeof programSchema>;
export type Catalog = z.infer<typeof catalogSchema>;
export type SourceRecord = {organization:Organization; program:Program; activity:Activity};
export const verificationLifetime = 24 * 60 * 60 * 1000;

// UUIDv5 DNS namespace. Slugs are source identities, never titles or refresh timestamps.
export function catalogId(kind:'organization'|'program'|'activity'|'schedule', key:string) {
  const hash = createHash('sha1').update(Buffer.from('6ba7b8109dad11d180b400c04fd430c8','hex'))
    .update(`dearby/catalog/${kind}/${key}`).digest().subarray(0,16);
  hash[6] = (hash[6] & 15) | 80;
  hash[8] = (hash[8] & 63) | 128;
  const hex = hash.toString('hex');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

export function atTime(activity:Activity, now:number, failed=false):Activity {
  const checked = activity.sourceCheckedAt === null ? NaN : Date.parse(activity.sourceCheckedAt);
  const until = activity.validUntil === null ? NaN : Date.parse(activity.validUntil);
  const verified = activity.freshness === 'verified' && !failed && checked <= now && now < until && until <= checked + verificationLifetime;
  const freshness = failed ? 'unavailable' : verified ? 'verified' : activity.freshness === 'unavailable' ? 'unavailable' : 'stale';
  let recruitmentStatus = activity.recruitmentStatus;
  if (activity.recruitmentEndAt !== null && now >= Date.parse(activity.recruitmentEndAt)) recruitmentStatus = 'closed';
  else if (freshness !== 'verified') recruitmentStatus = recruitmentStatus === 'closed' ? 'closed' : 'unknown';
  else if (activity.recruitmentStartAt !== null && now < Date.parse(activity.recruitmentStartAt) && recruitmentStatus === 'open') recruitmentStatus = 'scheduled';
  const isRecruiting = freshness === 'verified' && recruitmentStatus === 'open';
  return {...activity, freshness, recruitmentStatus, isRecruiting,
    sourceNote:activity.sourceNote + (failed ? ' 최근 수집/해석 실패: 마지막 정상 자료 보존, 현재 모집 미확인.' : '')};
}

export function readCatalog(db:DB, now=Date.now()):Catalog {
  const organizations = db.prepare('SELECT content FROM catalog_organizations ORDER BY id').all() as {content:string}[];
  const programs = db.prepare('SELECT content FROM catalog_programs ORDER BY id').all() as {content:string}[];
  const activities = db.prepare('SELECT content, last_failure FROM catalog_activities ORDER BY id').all() as {content:string;last_failure:string|null}[];
  return {generatedAt:new Date(now).toISOString(), organizations:organizations.map(r => organizationSchema.parse(JSON.parse(r.content))),
    programs:programs.map(r => programSchema.parse(JSON.parse(r.content))),
    activities:activities.map(r => atTime(activitySchema.parse(JSON.parse(r.content)), now, r.last_failure !== null))};
}

// One source record per round: a failure cannot replace the last good content or checked time.
export function storeSource(db:DB, sourceKey:string, record:SourceRecord, attemptedAt:string, bodyHash:string|null, historical=false) {
  const organization = organizationSchema.parse(record.organization);
  const program = programSchema.parse(record.program);
  const activity = activitySchema.parse(record.activity);
  if (program.organizationId !== organization.id || activity.organizationId !== organization.id || activity.programId !== program.id ||
      activity.id !== catalogId('activity',sourceKey)) throw new Error('Invalid catalog references');
  if (historical && (activity.freshness !== 'stale' || activity.sourceCheckedAt !== null || activity.validUntil !== null)) throw new Error('Historical verification forbidden');
  if (!historical && (activity.freshness !== 'verified' || activity.sourceCheckedAt !== attemptedAt || activity.validUntil === null ||
      Date.parse(activity.validUntil) !== Date.parse(attemptedAt) + verificationLifetime || !bodyHash)) throw new Error('Semantic verification required');
  db.transaction(() => {
    // Reimporting history must never replace newer verified (or failed-refresh) content.
    if (historical && db.prepare('SELECT 1 FROM catalog_activities WHERE source_key = ?').get(sourceKey)) return;
    db.prepare(`INSERT INTO catalog_organizations VALUES (?,?) ON CONFLICT(id) DO ${historical ? 'NOTHING' : 'UPDATE SET content=excluded.content'}`)
      .run(organization.id,JSON.stringify(organization));
    db.prepare(`INSERT INTO catalog_programs VALUES (?,?,?) ON CONFLICT(id) DO ${historical ? 'NOTHING' : 'UPDATE SET organization_id=excluded.organization_id,content=excluded.content'}`)
      .run(program.id,organization.id,JSON.stringify(program));
    db.prepare(`INSERT INTO catalog_activities VALUES (?,?,?,?,?,NULL,?) ON CONFLICT(id) DO UPDATE SET
      program_id=excluded.program_id,organization_id=excluded.organization_id,content=excluded.content,last_failure=NULL,good_body_sha256=excluded.good_body_sha256`)
      .run(activity.id,program.id,organization.id,sourceKey,JSON.stringify({...activity,isRecruiting:false}),bodyHash);
    db.prepare('INSERT OR REPLACE INTO catalog_refreshes VALUES (?,?,?,?,?)').run(sourceKey,attemptedAt,1,bodyHash,historical ? 'Historical import; not semantic verification' : 'Parsed official evidence');
  }).immediate();
}

export function recordFailure(db:DB, sourceKey:string, attemptedAt:string, note:string) {
  db.transaction(() => {
    db.prepare('UPDATE catalog_activities SET last_failure=? WHERE source_key=?').run(attemptedAt,sourceKey);
    db.prepare('INSERT OR REPLACE INTO catalog_refreshes VALUES (?,?,0,NULL,?)').run(sourceKey,attemptedAt,note);
  }).immediate();
}
