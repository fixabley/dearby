import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash,createHmac,randomUUID } from 'node:crypto';
import { mkdtempSync,rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fixture } from './helpers.js';
import { openDatabase } from '../src/database.js';
import { importSQLite } from '../src/import-sqlite.js';
const hash=(s:string)=>createHash('sha256').update(s).digest('hex');
test('nonempty 14-table SQLite import is exact, atomic, repeatable and conflict closed with existing tokens',async t=>{
 const f=await fixture(undefined,'test-only-proxy-secret-not-for-runtime');t.after(f.close);
 const dir=mkdtempSync(join(tmpdir(),'dearby-import-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));const path=join(dir,'source.sqlite');const sqlite=openDatabase(path);t.after(()=>sqlite.close());
 const owner=randomUUID(),other=randomUUID(),cardId=randomUUID(),revoked=randomUUID(),receipt=randomUUID(),challenge=randomUUID();
 const authToken='a'.repeat(43),guestToken='g'.repeat(43),code='123456',time=new Date().toISOString();
 const profile={id:owner,name:'Owner',job:'',introduction:'',contacts:[{id:randomUUID(),kind:'email',label:'Private',value:'private@example.com'}],histories:[],updatedAt:time};
 const card={id:cardId,ownerId:owner,name:'Public',description:'',profileName:'Owner',job:'',introduction:'',contacts:[],histories:[],createdAt:time};
 const input={cardId,recipientProfileId:other,context:{activityId:null,label:'Original'},requestId:randomUUID()};
 sqlite.prepare('INSERT INTO profiles VALUES(?,?,?)').run(owner,'owner@example.com',JSON.stringify(profile));sqlite.prepare('INSERT INTO profiles VALUES(?,?,?)').run(other,'other@example.com',JSON.stringify({...profile,id:other}));
 sqlite.prepare('INSERT INTO sessions VALUES(?,?,?)').run(hash(authToken),owner,Date.now()+86400000);
 sqlite.prepare('INSERT INTO challenges VALUES(?,?,?,?,?,?)').run(challenge,'owner@example.com',createHmac('sha256','test-only-secret-not-used-outside-tests').update(`${challenge}:${code}`).digest('hex'),Date.now()+300000,2,0);
 sqlite.prepare('INSERT INTO rate_limits VALUES(?,?,?)').run('original-rate-digest',Date.now(),3);
 sqlite.prepare('INSERT INTO cards(rowid,id,owner_id,data,revoked) VALUES(?,?,?,?,?)').run(42,cardId,owner,JSON.stringify(card),0);
 sqlite.prepare('INSERT INTO cards(rowid,id,owner_id,data,revoked) VALUES(?,?,?,?,?)').run(88,revoked,owner,JSON.stringify({...card,id:revoked}),1);
 sqlite.prepare('INSERT INTO receipts(rowid,id,recipient_id,card_id,context,received_at) VALUES(?,?,?,?,?,?)').run(19,receipt,other,cardId,JSON.stringify(input.context),time);
 sqlite.prepare('INSERT INTO exchanges VALUES(?,?,?,?,?)').run(owner,input.requestId,JSON.stringify(input),receipt,time);
 sqlite.prepare('INSERT INTO guest_sessions VALUES(?)').run(hash(guestToken));sqlite.prepare('INSERT INTO guest_cards(rowid,session_digest,card_id) VALUES(?,?,?)').run(55,hash(guestToken),cardId);
 sqlite.prepare('INSERT INTO catalog_organizations VALUES(?,?)').run('legacy-org','{ "kept": true }');sqlite.prepare('INSERT INTO catalog_programs VALUES(?,?,?)').run('legacy-program','legacy-org','{}');
 sqlite.prepare('INSERT INTO catalog_activities VALUES(?,?,?,?,?,?,?)').run('legacy-activity','legacy-program','legacy-org','legacy-source','{}','original failure','original hash');
 sqlite.prepare('INSERT INTO catalog_refreshes VALUES(?,?,?,?,?)').run('legacy-source',time,1,'original hash','original note');
 const dry=await importSQLite(f.db,path);assert.equal(dry.mode,'dry-run');assert.equal(dry.tables.length,14);assert.ok(dry.tables.every(x=>x.source.count>0&&x.target.count===0));
 // Runtime cannot reset sequences. A failed nonempty import must roll every data table back.
 await assert.rejects(importSQLite(f.db,path,true));assert.equal(await f.db.profile.count(),0);
 await f.admin.query('GRANT UPDATE ON ALL SEQUENCES IN SCHEMA dearby_api TO dearby_api_runtime');
 try {
  const applied=await importSQLite(f.db,path,true);assert.equal(applied.mode,'imported');assert.ok(applied.tables.every(x=>x.equal));
  assert.equal((await importSQLite(f.db,path,true)).mode,'unchanged');
 } finally {await f.admin.query('REVOKE UPDATE ON ALL SEQUENCES IN SCHEMA dearby_api FROM dearby_api_runtime');}
 assert.equal((await f.request('GET','/profile',undefined,authToken)).body.contacts[0].value,'private@example.com');
 assert.deepEqual((await f.request('GET',`/cards/${cardId}`)).body,card);assert.equal((await f.request('GET',`/cards/${revoked}`)).status,404);
 const wallet=await f.app.inject({url:'/v1/guest/cards',headers:{'x-guest-proxy-key':'test-only-proxy-secret-not-for-runtime','x-guest-token':guestToken}});assert.deepEqual(wallet.json(),{items:[card],shares:[]});
 assert.equal((await f.request('POST','/auth/sessions',{challengeId:challenge,code})).status,200);
 const newCard=await f.request('POST','/cards',{name:'After import',description:'',contactIds:[],historyIds:[]},authToken);assert.equal(newCard.status,201);assert.ok((await f.db.card.findUniqueOrThrow({where:{id:newCard.body.id}})).ordinal>88n);
 await assert.rejects(importSQLite(f.db,path,true)); // Changed target is never overwritten.
 assert.equal((await f.db.legacyOrganization.findUniqueOrThrow({where:{id:'legacy-org'}})).content,'{ "kept": true }');
});
test('empty ordered tables import existing rate limits/history without temporary sequence grant',async t=>{
 const f=await fixture();t.after(f.close);const dir=mkdtempSync(join(tmpdir(),'dearby-empty-import-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));const path=join(dir,'source.sqlite');const s=openDatabase(path);s.prepare('INSERT INTO rate_limits VALUES(?,?,?)').run('preserved',1234567890123,3);s.close();
 const result=await importSQLite(f.db,path,true);assert.equal(result.mode,'imported');assert.ok(result.tables.every(t=>t.equal));assert.equal((await f.db.rateLimit.findUniqueOrThrow({where:{key:'preserved'}})).windowStart,1234567890123n);
});

test('ambiguous legacy boolean fails closed before any target write',async t=>{
 const f=await fixture();t.after(f.close);const dir=mkdtempSync(join(tmpdir(),'dearby-invalid-import-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));const path=join(dir,'source.sqlite');const s=openDatabase(path);
 s.prepare('INSERT INTO profiles VALUES(?,?,?)').run('fixture-owner','owner@example.com','{}');s.prepare('INSERT INTO cards VALUES(?,?,?,?)').run('fixture-card','fixture-owner','{}',2);s.close();
 await assert.rejects(importSQLite(f.db,path),/Unexpected SQLite boolean/);await assert.rejects(importSQLite(f.db,path,true),/Unexpected SQLite boolean/);assert.equal(await f.db.profile.count(),0);
});
