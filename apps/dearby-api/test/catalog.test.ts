import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { fixture } from './offline-helpers.js';
import { catalogId, catalogSchema, storeSource, verificationLifetime } from '../src/catalog.js';
import { fetchOfficial, parseOfficial, refreshSource, type SourceKey } from '../src/catalog-sources.js';

const checkedAt = '2026-09-27T03:00:00.000Z';
const checked = Date.parse(checkedAt);
const html = (source:SourceKey) => readFileSync(new URL(`fixtures/${source}.html`,import.meta.url),'utf8');
const goodFetch:typeof fetch = async input => new Response(html(String(input).includes('kakao') ? 'kakao-2026' : 'feconf-2026'),{headers:{'content-type':'text/html'}});
const badFetch:typeof fetch = async () => { throw new Error('network failure'); };
const activity = async (f:Awaited<ReturnType<typeof fixture>>) => (await f.request('GET','/catalog')).body.activities[0];

test('public HTTP empty catalog is exact typed DTO; storage errors are 500, never empty success',async () => {
  const f = await fixture();
  try {
    f.setClock(checked);
    const response = await f.request('GET','/catalog');
    assert.equal(response.status,200);
    assert.equal(response.headers.get('cache-control'),'no-store');
    assert.deepEqual(response.body,{generatedAt:checkedAt,organizations:[],programs:[],activities:[]});
    catalogSchema.parse(response.body);
    f.db.exec('DROP TABLE catalog_activities');
    assert.equal((await f.request('GET','/catalog')).status,500);
  } finally { await f.close(); }
});

test('HTTP recruitment starts inclusive, deadline exclusive, semantic freshness exactly 24h, clock reversal closed',async () => {
  const f = await fixture();
  try {
    const record = parseOfficial('kakao-2026',html('kakao-2026'),checkedAt);
    // Synthetic times exercise the domain separately from the actual source parser.
    record.activity.recruitmentStartAt = new Date(checked + 1000).toISOString();
    record.activity.recruitmentEndAt = new Date(checked + 2000).toISOString();
    storeSource(f.db,'kakao-2026',record,checkedAt,'fixture-sha');
    for (const [offset,status,recruiting] of [[-1,'unknown',false],[0,'scheduled',false],[999,'scheduled',false],[1000,'open',true],[1999,'open',true],[2000,'closed',false]] as const) {
      f.setClock(checked + offset);
      const result = await activity(f);
      assert.equal(result.recruitmentStatus,status); assert.equal(result.isRecruiting,recruiting);
    }
    record.activity.recruitmentStartAt = null; record.activity.recruitmentEndAt = null;
    storeSource(f.db,'kakao-2026',record,checkedAt,'fixture-sha');
    f.setClock(checked + verificationLifetime - 1);
    assert.equal((await activity(f)).isRecruiting,true);
    f.advance(1);
    assert.equal((await activity(f)).freshness,'stale'); assert.equal((await activity(f)).isRecruiting,false);
    assert.equal((await activity(f)).recruitmentStatus,'unknown');
    assert.equal((await f.request('GET','/catalog')).body.activities.length,1);
    f.setClock(checked - 1); assert.equal((await activity(f)).freshness,'stale');
    record.activity.validUntil = new Date(checked + verificationLifetime + 1).toISOString();
    assert.throws(() => storeSource(f.db,'kakao-2026',record,checkedAt,'fixture-sha'),/Semantic/);
  } finally { await f.close(); }
});

test('offline parsers distinguish real application opening from event opening, dates and uncertainty',() => {
  assert.equal(catalogId('activity','kakao-2026'),'ed43a1d2-213c-5511-bfe2-e80aa26cd866');
  const kakao = parseOfficial('kakao-2026',html('kakao-2026'),checkedAt).activity;
  assert.equal(kakao.recruitmentStatus,'open'); assert.equal(kakao.participationType,'selection');
  assert.equal(kakao.recruitmentEndAt,'2026-09-28T03:00:00.000Z');
  assert.equal(kakao.recruitmentStartAt,null); assert.equal(kakao.schedules[0].startAt,null); assert.equal(kakao.schedules[0].endAt,null);
  const feconf = parseOfficial('feconf-2026',html('feconf-2026'),checkedAt).activity;
  assert.equal(feconf.recruitmentStatus,'scheduled'); assert.equal(feconf.recruitmentStartAt,null); assert.equal(feconf.recruitmentEndAt,null);
  assert.equal(feconf.schedules[0].startAt,'2026-10-24T01:00:00.000Z'); assert.equal(feconf.schedules[0].endAt,null);
  assert.throws(() => parseOfficial('feconf-2026',html('feconf-2026').replaceAll('TICKET OPEN D-14',''),checkedAt),/PARSER_UNCERTAIN/);
  assert.throws(() => parseOfficial('feconf-2026',html('feconf-2026').replaceAll('D-14','D-0'),checkedAt),/PARSER_UNCERTAIN/);
  for (const changed of [html('kakao-2026').replace('OPEN','CLOSED'),html('kakao-2026').replace('28일 낮 12시','29일 낮 12시'),`<script>${html('kakao-2026')}</script>`,html('kakao-2026') + html('kakao-2026')]) {
    assert.throws(() => parseOfficial('kakao-2026',changed,checkedAt),/PARSER_UNCERTAIN/);
  }
});

test('HTTP failed fetch/parser keeps prior good body and verification time; success recovers stable IDs',async () => {
  const f = await fixture();
  try {
    f.setClock(checked);
    assert.equal((await refreshSource(f.db,'kakao-2026',() => checked,goodFetch)).succeeded,true);
    const before = await activity(f);
    const storedHash = f.db.prepare('SELECT good_body_sha256 FROM catalog_activities').get();
    for (const fetcher of [badFetch,async () => new Response('<html>HTTP 200 but no evidence</html>',{headers:{'content-type':'text/html'}})]) {
      assert.equal((await refreshSource(f.db,'kakao-2026',() => checked + 1000,fetcher)).succeeded,false);
      const after = await activity(f);
      assert.equal(after.id,before.id); assert.equal(after.summary,before.summary);
      assert.equal(after.sourceCheckedAt,before.sourceCheckedAt); assert.equal(after.validUntil,before.validUntil);
      assert.equal(after.isRecruiting,false); assert.equal(after.freshness,'unavailable');
      assert.deepEqual(f.db.prepare('SELECT good_body_sha256 FROM catalog_activities').get(),storedHash);
    }
    assert.equal((await refreshSource(f.db,'kakao-2026',() => checked + 1000,goodFetch)).succeeded,true);
    f.setClock(checked + 1000);
    const recovered = await activity(f);
    assert.equal(recovered.id,before.id); assert.equal(recovered.isRecruiting,true);
    assert.equal(recovered.sourceCheckedAt,new Date(checked+1000).toISOString());
    catalogSchema.parse((await f.request('GET','/catalog')).body);
  } finally { await f.close(); }
});

test('SQLite failed refresh rolls back organization/program/content writes and retains last good data',async () => {
  const f = await fixture();
  try {
    const record = parseOfficial('kakao-2026',html('kakao-2026'),checkedAt);
    record.organization.name = 'Previous good name';
    storeSource(f.db,'kakao-2026',record,checkedAt,'fixture-sha');
    f.db.exec("CREATE TRIGGER fail_catalog BEFORE UPDATE OF content ON catalog_activities BEGIN SELECT RAISE(ABORT, 'disk failure'); END");
    assert.equal((await refreshSource(f.db,'kakao-2026',() => checked+1000,goodFetch)).succeeded,false);
    const result = (await f.request('GET','/catalog')).body;
    assert.equal(result.organizations[0].name,'Previous good name');
    assert.equal(result.activities[0].sourceCheckedAt,checkedAt);
    assert.equal(result.activities[0].freshness,'unavailable');
    f.db.exec("CREATE TRIGGER fail_insert BEFORE INSERT ON catalog_activities BEGIN SELECT RAISE(ABORT, 'disk failure'); END");
    assert.equal((await refreshSource(f.db,'feconf-2026',() => checked,goodFetch)).succeeded,false);
    const after = (await f.request('GET','/catalog')).body;
    assert.equal(after.organizations.length,1); assert.equal(after.programs.length,1); assert.equal(after.activities.length,1);
  } finally { await f.close(); }
});

test('bounded collector rejects unknown URLs, redirects, types, status and oversized bodies',async () => {
  await assert.rejects(fetchOfficial('https://evil.invalid' as SourceKey,goodFetch),/SOURCE_NOT_ALLOWED/);
  for (const [response,expected] of [[new Response('',{status:503}),'SOURCE_HTTP_ERROR'],[new Response('{}'),'SOURCE_CONTENT_TYPE'],
    [new Response('x',{headers:{'content-type':'text/html','content-length':'1048577'}}),'SOURCE_TOO_LARGE'],
    [new Response('x'.repeat(1048577),{headers:{'content-type':'text/html'}}),'SOURCE_TOO_LARGE']] as const) {
    await assert.rejects(fetchOfficial('kakao-2026',async (_url,options) => {
      assert.equal(options?.redirect,'error'); assert.ok(options?.signal); return response;
    }),new RegExp(expected));
  }
  // Real HTTP transfer verifies streaming/redirect behavior without hitting official sites in tests.
  const server = createServer((request,response) => {
    if (request.url === '/redirect') { response.writeHead(302,{Location:'/ok'}); response.end(); }
    else { response.writeHead(200,{'content-type':'text/html'}); response.end(html('kakao-2026')); }
  });
  await new Promise<void>(resolve => server.listen(0,'127.0.0.1',resolve));
  try {
    const address = server.address(); assert.ok(address && typeof address !== 'string');
    const base = `http://127.0.0.1:${address.port}`;
    assert.equal(await fetchOfficial('kakao-2026',(_input,options) => fetch(`${base}/ok`,options)),html('kakao-2026'));
    await assert.rejects(fetchOfficial('kakao-2026',(_input,options) => fetch(`${base}/redirect`,options)));
  } finally { await new Promise<void>((resolve,reject) => server.close(error => error ? reject(error) : resolve())); }
});

test('official noon deadline closes at exact instant even with fresh OPEN source; scheduled details stay available',async () => {
  const f = await fixture();
  try {
    const start = Date.parse('2026-09-27T04:00:00.000Z');
    const deadline = Date.parse('2026-09-28T03:00:00.000Z');
    await refreshSource(f.db,'kakao-2026',() => start,goodFetch);
    await refreshSource(f.db,'feconf-2026',() => start,goodFetch);
    f.setClock(deadline - 1);
    let result = catalogSchema.parse((await f.request('GET','/catalog')).body);
    assert.equal(result.activities.filter(a => a.isRecruiting).length,1);
    f.advance(1);
    result = catalogSchema.parse((await f.request('GET','/catalog')).body);
    assert.equal(result.activities.length,2);
    assert.ok(result.activities.every(a => !a.isRecruiting && a.freshness === 'verified'));
    assert.equal(result.activities.find(a => a.title === 'if(kakao)26')?.recruitmentStatus,'closed');
    assert.equal(result.activities.find(a => a.title === 'FECONF 2026')?.recruitmentStatus,'scheduled');
  } finally { await f.close(); }
});

test('failed initial collection leaves truthful empty discovery with durable failure evidence',async () => {
  const f = await fixture();
  try {
    assert.equal((await refreshSource(f.db,'kakao-2026',() => checked,badFetch)).succeeded,false);
    const result = await f.request('GET','/catalog');
    assert.equal(result.status,200); assert.deepEqual(result.body.activities,[]);
    assert.deepEqual(f.db.prepare('SELECT source_key,succeeded,note FROM catalog_refreshes').get(),
      {source_key:'kakao-2026',succeeded:0,note:'SOURCE_REFRESH_FAILED'});
  } finally { await f.close(); }
});
