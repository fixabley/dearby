import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fixture } from './helpers.js';
import { catalogId, catalogSchema, storeSource } from '../src/catalog.js';
import { parseOfficial, refreshSource } from '../src/catalog-sources.js';
import { importLegacy } from '../src/catalog-legacy.js';
import { openDatabase } from '../src/database.js';
import { createApp } from '../src/app.js';
const checkedAt = '2026-09-27T03:00:00.000Z';
const checked = Date.parse(checkedAt);
const html = (_source:string) => readFileSync(new URL('fixtures/kakao-2026.html',import.meta.url),'utf8');
const goodFetch:typeof fetch = async () => new Response(html('kakao-2026'),{headers:{'content-type':'text/html'}});

test('legacy is always stale, idempotent and cannot overwrite live data; UUID references survive reopen',async () => {
  const f = await fixture();
  const legacy = JSON.parse(readFileSync(new URL('../../../shared/data/catalog-snapshot-2026-09-24.json',import.meta.url),'utf8'));
  try {
    f.setClock(checked);
    importLegacy(f.db,legacy,checked);
    const original = (await f.request('GET','/catalog')).body;
    assert.ok(original.activities.length > 2);
    assert.ok(original.activities.every((a: {freshness:string;isRecruiting:boolean;sourceCheckedAt:null}) => a.freshness === 'stale' && !a.isRecruiting && a.sourceCheckedAt === null));
    importLegacy(f.db,legacy,checked);
    assert.deepEqual((await f.request('GET','/catalog')).body,original);
    await refreshSource(f.db,'kakao-2026',() => checked,goodFetch);
    importLegacy(f.db,legacy,checked);
    const catalog = catalogSchema.parse((await f.request('GET','/catalog')).body);
    assert.equal(catalog.activities.length,original.activities.length);
    assert.equal(catalog.activities.find(a => a.id === catalogId('activity','kakao-2026'))?.isRecruiting,true);
    for (const a of catalog.activities) {
      const p = catalog.programs.find(p => p.id === a.programId);
      assert.equal(p?.organizationId,a.organizationId);
      assert.ok(catalog.organizations.some(o => o.id === a.organizationId));
      assert.match(a.id,/^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    }
    const bad = parseOfficial('kakao-2026',html('kakao-2026'),checkedAt);
    bad.activity.organizationId = catalogId('organization','wrong');
    assert.throws(() => storeSource(f.db,'kakao-2026',bad,checkedAt,'fixture-sha'),/references/);
    await f.app.close(); f.db.close();
    const db = openDatabase(f.path);
    const {app} = createApp(db,{otpSecret:'test-only-secret-not-used-outside-tests',sendCode:async () => {},now:() => checked});
    try {
      await app.listen({host:'127.0.0.1',port:0});
      const address = app.server.address(); assert.ok(address && typeof address !== 'string');
      assert.deepEqual(await (await fetch(`http://127.0.0.1:${address.port}/v1/catalog`)).json(),catalog);
      assert.equal((db.prepare('SELECT COUNT(*) AS n FROM migrations').get() as {n:number}).n,2);
    } finally { await app.close(); db.close(); }
  } finally { await f.close(); }
});

