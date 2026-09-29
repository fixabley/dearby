import { test } from 'node:test';
import assert from 'node:assert/strict';
import { supabaseCatalog } from '../src/catalog-supabase.js';
import { ApiError } from '../src/validation.js';
const env = {CATALOG_BACKEND:'supabase',SUPABASE_URL:'http://127.0.0.1:54321',SUPABASE_ANON_KEY:'test'};
test('catalog backend is explicit, configured and secure',() => {
  assert.equal(supabaseCatalog({}),undefined);
  assert.throws(() => supabaseCatalog({CATALOG_BACKEND:'mistyped'}));
  assert.throws(() => supabaseCatalog({CATALOG_BACKEND:'supabase'}));
  assert.throws(() => supabaseCatalog({...env,SUPABASE_URL:'http://untrusted.example'}));
});
test('Supabase atomic public snapshot request preserves existing DTO',async () => {
  const read = supabaseCatalog(env,async (url,options) => {
    assert.equal(String(url),'http://127.0.0.1:54321/rest/v1/rpc/catalog_public_snapshot');
    assert.equal(options?.method,'POST'); assert.equal(options?.redirect,'error');
    return Response.json({generatedAt:new Date().toISOString(),organizations:[],programs:[],activities:[]});
  })!;
  assert.deepEqual((await read()).activities,[]);
});
test('Supabase failures and malformed successful responses are 503 without fallback or leaked credentials',async () => {
  for (const fetcher of [async () => new Response('key secret',{status:500}),async () => Response.json({activities:[]}),async () => {throw new Error('secret');}]) {
    await assert.rejects(supabaseCatalog(env,fetcher)!, (error:unknown) => error instanceof ApiError && error.statusCode === 503 && !error.message.includes('secret'));
  }
});

test('Docker host is allowed but credentials, redirects and arbitrary insecure hosts are rejected', () => {
  assert.ok(supabaseCatalog({...env,SUPABASE_URL:'http://host.docker.internal:54321'}));
  for (const url of ['malformed secret URL','http://192.168.0.20:54321','https://user:password@example.com','https://example.com?key=secret','https://example.com#secret']) {
    assert.throws(() => supabaseCatalog({...env,SUPABASE_URL:url}));
  }
});

test('HTTP catalog failure is sanitized and leaves SQLite auth protected', async () => {
  const {openDatabase} = await import('../src/database.js');
  const {createApp} = await import('../src/app.js');
  const db = openDatabase(':memory:');
  const {app} = createApp(db,{otpSecret:'test-only-secret-not-used-outside-tests',sendCode:async () => {throw new Error('SMTP credential');},
    catalogReader:supabaseCatalog(env,async () => {throw new Error('Supabase credential');})});
  try {
    const response = await app.inject({url:'/v1/catalog',headers:{authorization:'Bearer secret','x-forwarded-for':'1.2.3.4'}});
    assert.equal(response.statusCode,503);
    assert.deepEqual(response.json(),{error:{code:'CATALOG_UNAVAILABLE',message:'Activity catalog temporarily unavailable'}});
    assert.equal(response.headers['cache-control'],'no-store');
    assert.equal((await app.inject('/v1/profile')).statusCode,401);
    for (let i=0;i<21;i++) {
      const challenge = await app.inject({method:'POST',url:'/v1/auth/challenges',headers:{'x-forwarded-for':`192.0.2.${i}`},payload:{email:`proxy${i}@example.com`}});
      assert.equal(challenge.statusCode,i<20?503:429);
      assert.ok(!challenge.body.includes('credential'));
    }
    assert.equal((db.prepare('SELECT count(*) AS n FROM challenges').get() as {n:number}).n,0);
  } finally {await app.close();db.close();}
});
