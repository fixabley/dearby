import { createHash } from 'node:crypto';
import type { DB } from './database.js';
import { catalogId, recordFailure, storeSource, verificationLifetime, type Activity, type SourceRecord } from './catalog.js';

export const officialSources = {
  'kakao-2026': 'https://if.kakao.com/2026',
  'feconf-2026': 'https://2026.feconf.kr/',
} as const;
export type SourceKey = keyof typeof officialSources;
const MAX_BYTES = 1024 * 1024;
function text(html:string) {
  return html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,' ').replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi,' ')
    .replace(/<!--[\s\S]*?-->/g,' ').replace(/<[^>]+>/g,' ').replace(/&nbsp;|&#160;/g,' ').replace(/\s+/g,' ').trim();
}
function requireEvidence(condition:unknown):asserts condition {
  if (!condition) throw new Error('PARSER_UNCERTAIN');
}
export function parseOfficial(sourceKey:SourceKey, html:string, checkedAt:string):SourceRecord {
  const kakao = sourceKey === 'kakao-2026';
  const programKey = kakao ? 'kakao' : 'feconf';
  const organizationKey = kakao ? 'org-kakao' : 'org-fedg';
  const organization = {id:catalogId('organization',organizationKey), name:kakao ? '카카오' : 'FEConf 준비위원회 · FEDG', description:''};
  const program = {id:catalogId('program',programKey), organizationId:organization.id, title:kakao ? 'if(kakao)' : 'FEConf', description:''};
  const page = text(html);
  const activity:Activity = {
    id:catalogId('activity',sourceKey), programId:program.id, organizationId:organization.id,
    title:kakao ? 'if(kakao)26' : 'FECONF 2026', summary:'', participationType:kakao ? 'selection' : 'registration',
    recruitmentStatus:'unknown', isRecruiting:false, recruitmentStartAt:null, recruitmentEndAt:null,
    dateLabel:'', location:null, cost:null, audience:null, qualification:null, roles:[], schedules:[],
    officialUrl:officialSources[sourceKey], applicationUrl:null, sourceCheckedAt:checkedAt,
    validUntil:new Date(Date.parse(checkedAt)+verificationLifetime).toISOString(), freshness:'verified', sourceNote:'',
  };
  if (kakao) {
    const sections = [...html.matchAll(/<section\b[^>]*class="section_apply"[^>]*>([\s\S]*?)<\/section>/g)];
    requireEvidence(sections.length === 1);
    const apply = text(sections[0][1]);
    // Deliberately pinned to reviewed 2026 markup and round. A changed date/state needs review.
    requireEvidence(page.includes('2026.10.13(Tue) - 14(Wed) @Kakao AI Campus') &&
      apply.includes('참가 신청 OPEN') && apply.includes('~ 9월 28일 낮 12시까지') &&
      !/마감|종료|CLOSED|준비|예정.*OPEN/i.test(apply) &&
      page.includes('9월 7일부터 9월 28일까지 참가 신청 기간') &&
      page.includes('웹사이트 참가 신청자 중 선정된 분들') && page.includes('만 18세 이상의 성년'));
    activity.recruitmentStatus = 'open';
    // Starting day has no hour. Explicit OPEN is sufficient; don't invent a start instant.
    activity.recruitmentEndAt = '2026-09-28T03:00:00.000Z';
    activity.dateLabel = '2026.10.13(Tue) - 14(Wed)';
    activity.location = 'Kakao AI Campus';
    activity.summary = '카카오 개발자 컨퍼런스. 신청자 중 선정된 참가자만 오프라인 참석 가능합니다.';
    activity.audience = '만 18세 이상 성년';
    activity.qualification = '신청자 중 최종 선정; 참가 신청은 1일만 가능';
    requireEvidence(page.includes('참가 신청은 1일만 가능합니다') && page.includes('오프라인, 온라인 모두 무료'));
    activity.cost = '무료';
    activity.applicationUrl = officialSources[sourceKey];
    activity.sourceNote = '공식 신청 기간은 9월 7일부터 9월 28일 낮 12시까지(한국 시간)입니다. 정확한 신청 시작 시각과 행사 시간은 확인되지 않았습니다. 신청자 중 참가자를 선정합니다.';
  } else {
    requireEvidence(page.includes('FECONF 2026') && page.includes('FECONF BY FEDG. 2026.10.24 SAT. 10:00 OPEN. LOTTE TOWER 31F SEOUL, KOREA'));
    const countdowns = [...page.matchAll(/TICKET OPEN D-(\d+)\b/g)];
    requireEvidence(countdowns.length > 0 && countdowns.every(m => Number(m[1]) > 0 && m[1] === countdowns[0][1]) &&
      !/SOLD OUT|매진|티켓 구매|TICKET BUY/i.test(page));
    activity.recruitmentStatus = 'scheduled';
    activity.dateLabel = '2026.10.24 SAT. 10:00 OPEN';
    activity.location = 'LOTTE TOWER 31F SEOUL, KOREA';
    activity.summary = 'FEDG 프론트엔드 개발자 컨퍼런스';
    activity.sourceNote = '공식 사이트는 티켓 오픈 전으로 안내하고 있습니다. 정확한 신청 시작일과 마감일은 아직 확인되지 않았습니다. 행사장은 10월 24일 오전 10시(한국 시간)에 열리며, 종료 시각은 확인되지 않았습니다.';
  }
  activity.schedules = [{id:catalogId('schedule',`${sourceKey}/main`),title:activity.title,
    startAt:kakao ? null : '2026-10-24T01:00:00.000Z',endAt:null,dateLabel:activity.dateLabel,timeZone:'Asia/Seoul'}];
  return {organization,program,activity};
}

export async function fetchOfficial(sourceKey:SourceKey, fetcher:typeof fetch=fetch) {
  if (!Object.hasOwn(officialSources,sourceKey)) throw new Error('SOURCE_NOT_ALLOWED');
  // No redirects, discovered links, credentials, scripts, retries or unbounded response bodies.
  const response = await fetcher(officialSources[sourceKey], {redirect:'error',signal:AbortSignal.timeout(15000),
    headers:{Accept:'text/html','User-Agent':'DearbyCatalog/1.0 (bounded official-source verification)'}});
  if (!response.ok) { await response.body?.cancel(); throw new Error('SOURCE_HTTP_ERROR'); }
  if (!response.headers.get('content-type')?.toLowerCase().startsWith('text/html')) { await response.body?.cancel(); throw new Error('SOURCE_CONTENT_TYPE'); }
  if (Number(response.headers.get('content-length')) > MAX_BYTES) { await response.body?.cancel(); throw new Error('SOURCE_TOO_LARGE'); }
  if (!response.body) throw new Error('SOURCE_EMPTY');
  const reader = response.body.getReader();
  const chunks:Uint8Array[] = [];
  let length = 0;
  try {
    while (true) {
      const {done,value} = await reader.read();
      if (done) break;
      length += value.length;
      if (length > MAX_BYTES) throw new Error('SOURCE_TOO_LARGE');
      chunks.push(value);
    }
  } finally { await reader.cancel(); reader.releaseLock(); }
  return Buffer.concat(chunks).toString('utf8');
}

export async function refreshSource(db:DB, sourceKey:SourceKey, now:()=>number=Date.now, fetcher:typeof fetch=fetch) {
  if (!Object.hasOwn(officialSources,sourceKey)) throw new Error('SOURCE_NOT_ALLOWED');
  let checkedAt = new Date(now()).toISOString();
  try {
    const html = await fetchOfficial(sourceKey,fetcher);
    checkedAt = new Date(now()).toISOString();
    const record = parseOfficial(sourceKey,html,checkedAt);
    const bodyHash = createHash('sha256').update(html).digest('hex');
    storeSource(db,sourceKey,record,checkedAt,bodyHash);
    return {sourceKey,succeeded:true,checkedAt,bodyHash,bytes:Buffer.byteLength(html)};
  } catch (error) {
    const code = error instanceof Error && /^(PARSER_UNCERTAIN|SOURCE_[A-Z_]+)$/.test(error.message) ? error.message : 'SOURCE_REFRESH_FAILED';
    recordFailure(db,sourceKey,checkedAt,code);
    return {sourceKey,succeeded:false,checkedAt,error:code};
  }
}
