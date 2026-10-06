import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixture } from './helpers.js';
import {randomUUID} from 'node:crypto';
import { smtpMailer } from '../src/mail.js';
test('Prisma public RPC reads only published catalog; no SQLite or REST fallback',async t=>{
 const f=await fixture();t.after(f.close);
 const r=await f.request('GET','/catalog');assert.equal(r.status,200);assert.deepEqual(r.body.activities,[]);
 const org=randomUUID(),program=randomUUID();
 await f.admin.query('INSERT INTO public.catalog_organizations(id,name) VALUES($1,$2)',[org,'Fixture']);
 await f.admin.query('INSERT INTO public.catalog_programs(id,organization_id,title) VALUES($1,$2,$3)',[program,org,'Fixture']);
 for(const status of ['published','draft','hidden']) await f.admin.query('INSERT INTO public.catalog_activities(id,program_id,organization_id,title,official_url,publication_status) VALUES($1,$2,$3,$4,$5,$6)',[randomUUID(),program,org,status,'https://example.com/fixture',status]);
 const visible=await f.request('GET','/catalog');assert.equal(visible.status,200);assert.equal(visible.body.activities.length,1);assert.equal(visible.body.activities[0].title,'published');
 await assert.rejects(f.db.$queryRaw`SELECT * FROM public.catalog_activities`);
 await assert.rejects(f.db.$queryRaw`SELECT * FROM auth.users`);
 await f.admin.query('REVOKE EXECUTE ON FUNCTION public.catalog_public_snapshot() FROM dearby_api_runtime');
 const failure=await f.request('GET','/catalog');assert.equal(failure.status,503);
 assert.deepEqual(failure.body,{error:{code:'CATALOG_UNAVAILABLE',message:'Activity catalog temporarily unavailable'}});
});
test('HTTP upstream and mail failures sanitized, forwarded IP never trusted',async t=>{
 const f=await fixture(smtpMailer({}));t.after(f.close);
 // A failed delivery still consumes the address quota, and a forwarded header cannot reset it.
 for(let i=0;i<2;i++){
  const r=await f.app.inject({method:'POST',url:'/v1/auth/challenges',headers:{'x-forwarded-for':`192.0.2.${i}`},payload:{email:'proxy@example.com'}});
  assert.equal(r.statusCode,i<1?503:429);
 }
 assert.equal(await f.db.challenge.count(),0);
});
test('real snapshot returns organization parents with every ancestor; older snapshots without parentId read as top level; invalid parent fails closed',async t=>{
 const f=await fixture();t.after(f.close);
 const [root,middle,child,solo,unused]=Array.from({length:5},()=>randomUUID());
 for(const [id,parent] of [[root,null],[middle,root],[child,middle],[solo,null],[unused,root]] as const)
  await f.admin.query('INSERT INTO public.catalog_organizations(id,name,parent_id) VALUES($1,$2,$3)',[id,'Org '+id.slice(0,4),parent]);
 for(const org of [child,solo]){
  const program=randomUUID();
  await f.admin.query('INSERT INTO public.catalog_programs(id,organization_id,title) VALUES($1,$2,$3)',[program,org,'Fixture']);
  await f.admin.query('INSERT INTO public.catalog_activities(id,program_id,organization_id,title,official_url,publication_status) VALUES($1,$2,$3,$4,$5,$6)',[randomUUID(),program,org,'Fixture','https://example.com/fixture','published']);
 }
 // The migrated snapshot function: referenced organizations plus all ancestors, never unrelated ones.
 const real=await f.request('GET','/catalog');assert.equal(real.status,200);
 const parents=Object.fromEntries(real.body.organizations.map((o:{id:string;parentId:string|null})=>[o.id,o.parentId]));
 assert.deepEqual(parents,{[root]:null,[middle]:root,[child]:middle,[solo]:null});
 // Stand-in for a snapshot published before the hierarchy, and for a malformed parent.
 const snapshot=async(organizations:unknown[])=>f.admin.query(`CREATE OR REPLACE FUNCTION public.catalog_public_snapshot() RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
  SELECT public.catalog_public_snapshot_base() || jsonb_build_object('organizations','${JSON.stringify(organizations)}'::jsonb) $$`);
 await f.admin.query('ALTER FUNCTION public.catalog_public_snapshot() RENAME TO catalog_public_snapshot_base');
 await snapshot([{id:child,name:'Child',description:''},{id:solo,name:'Solo',description:''}]);
 await f.admin.query('GRANT EXECUTE ON FUNCTION public.catalog_public_snapshot() TO dearby_api_runtime');
 const old=await f.request('GET','/catalog');assert.equal(old.status,200);
 assert.deepEqual(old.body.organizations,[{id:child,name:'Child',description:'',parentId:null},{id:solo,name:'Solo',description:'',parentId:null}]);
 await snapshot([{id:child,name:'Child',description:'',parentId:'not-a-uuid'}]);
 assert.equal((await f.request('GET','/catalog')).status,503);
});
