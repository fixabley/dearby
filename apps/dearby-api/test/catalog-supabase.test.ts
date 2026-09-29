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
