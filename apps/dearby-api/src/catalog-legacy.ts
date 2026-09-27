import { z } from 'zod';
import type { DB } from './database.js';
import { catalogId, storeSource, type SourceRecord } from './catalog.js';

const nullable = z.string().nullable();
const legacySchema = z.object({snapshotDate:z.iso.date(),live:z.literal(false),
  organizations:z.array(z.object({id:z.string(),name:z.string(),description:z.string()})),
  programs:z.array(z.object({id:z.string(),orgId:z.string(),title:z.string(),subtitle:z.string(),notices:z.array(z.object({
    id:z.string(),round:z.string(),participationType:z.enum(['registration','application']),roles:z.array(z.string()),
    eventDate:nullable,eventEndDate:nullable,location:nullable,cost:nullable,audience:z.union([nullable,z.array(z.string())]).transform(v => Array.isArray(v) ? v.join(', ') : v),qualification:nullable,
    officialUrl:z.url({protocol:/^https?$/}),registrationUrl:z.url({protocol:/^https?$/}).nullable(),
  }))})),
});
export function importLegacy(db:DB, input:unknown, now=Date.now()) {
  const snapshot = legacySchema.parse(input);
  for (const ids of [snapshot.organizations.map(o => o.id),snapshot.programs.map(p => p.id),snapshot.programs.flatMap(p => p.notices.map(n => n.id))]) {
    if (new Set(ids).size !== ids.length) throw new Error('Duplicate legacy identity');
  }
  const organizations = new Map(snapshot.organizations.map(o => [o.id,o]));
  let count = 0;
  db.transaction(() => {
    for (const p of snapshot.programs) for (const n of p.notices) {
      const o = organizations.get(p.orgId);
      if (!o) throw new Error('Missing legacy organization');
      const organization = {id:catalogId('organization',o.id),name:o.name,description:o.description};
      const program = {id:catalogId('program',p.id),organizationId:organization.id,title:p.title,description:p.subtitle};
      const dateLabel = [n.eventDate,n.eventEndDate].filter(Boolean).join(' ~ ');
      const record:SourceRecord = {organization,program,activity:{
        id:catalogId('activity',n.id),programId:program.id,organizationId:organization.id,title:`${p.title} · ${n.round}`,
        summary:p.subtitle,participationType:n.participationType === 'application' ? 'selection' : 'registration',
        recruitmentStatus:'unknown',isRecruiting:false,recruitmentStartAt:null,recruitmentEndAt:null,
        dateLabel,location:n.location,cost:n.cost,audience:n.audience,qualification:n.qualification,
        roles:n.roles,schedules:[],officialUrl:n.officialUrl,applicationUrl:n.registrationUrl,
        sourceCheckedAt:null,validUntil:null,freshness:'stale',
        sourceNote:`${snapshot.snapshotDate}에 확인한 과거 안내입니다. 현재 모집 여부와 일정의 정확한 시각은 다시 확인이 필요합니다.`,
      }};
      storeSource(db,n.id,record,new Date(now).toISOString(),null,true);
      count++;
    }
  }).immediate();
  return count;
}
