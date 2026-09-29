import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {fixture} from './helpers.js';
import {createApp} from '../src/app.js';
import {openPostgres} from '../src/postgres.js';
const secret='fixture-only-proxy-secret-not-for-runtime';
test('concurrent OTP sends/attempts consume durable quotas and issue only one session',async t=>{
 const f=await fixture();t.after(f.close);
 const sends=await Promise.all(Array.from({length:10},()=>f.request('POST','/auth/challenges',{email:'race@example.com'})));
 assert.equal(sends.filter(r=>r.status===202).length,1);assert.equal(sends.filter(r=>r.status===429).length,9);
 const id=sends.find(r=>r.status===202)!.body.challengeId;
 const attempts=await Promise.all(Array.from({length:8},()=>f.request('POST','/auth/sessions',{challengeId:id,code:f.codes.get('race@example.com')})));
 assert.equal(attempts.filter(r=>r.status===200).length,1);assert.equal(await f.db.session.count(),1);
 f.advance(61000);const c=await f.request('POST','/auth/challenges',{email:'wrong@example.com'});const wrong=f.codes.get('wrong@example.com')==='000000'?'000001':'000000';
 await Promise.all(Array.from({length:8},()=>f.request('POST','/auth/sessions',{challengeId:c.body.challengeId,code:wrong})));
 assert.equal((await f.db.challenge.findUniqueOrThrow({where:{id:c.body.challengeId}})).attempts,5);
});
test('two API clients serialize guest last-slot capacity and duplicate saves globally',async t=>{
 const f=await fixture(undefined,secret);const owner=await f.login('owner@example.com');const card=(await f.request('POST','/cards',{name:'Card',description:'',contactIds:[],historyIds:[]},owner.sessionToken)).body;
 const db2=openPostgres({DATABASE_URL:f.connection});const {app:second}=createApp(db2,{otpSecret:'test-only-secret-not-used-outside-tests',guestProxySecret:secret,sendCode:async()=>{}});t.after(async()=>{await second.close();await db2.$disconnect();await f.close();});
 const put=(id:string,token?:string,i=0)=>(i%2?second:f.app).inject({method:'PUT',url:'/v1/guest/cards/'+id,headers:{'x-guest-proxy-key':secret,...(token?{'x-guest-token':token}:{})}});
 const first=await put(card.id);const token=first.json().guestToken;const session=(await f.db.guestSession.findFirstOrThrow()).digest;
 const cards=Array.from({length:105},()=>({...card,id:randomUUID()}));await f.db.card.createMany({data:cards.map(c=>({id:c.id,ownerId:owner.profileId,data:JSON.stringify(c)}))});
 await f.db.guestCard.createMany({data:cards.slice(0,98).map(c=>({sessionDigest:session,cardId:c.id}))});
 const responses=await Promise.all(cards.slice(98).map((c,i)=>put(c.id,token,i)));assert.equal(responses.filter(r=>r.statusCode===200).length,1);assert.equal(responses.filter(r=>r.statusCode===409).length,6);assert.equal(await f.db.guestCard.count(),100);
 await f.db.guestSession.createMany({data:Array.from({length:9998},(_,i)=>({digest:i.toString(16).padStart(64,'0')}))});
 const global=await Promise.all(Array.from({length:6},(_,i)=>put(card.id,undefined,i)));assert.equal(global.filter(r=>r.statusCode===201).length,1);assert.equal(global.filter(r=>r.statusCode===409).length,5);assert.equal(await f.db.guestSession.count(),10000);
});
