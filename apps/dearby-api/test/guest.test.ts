import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { fixture } from './helpers.js';
import { createApp } from '../src/app.js';
import { openPostgres } from '../src/postgres.js';

const secret = 'test-only-guest-proxy-secret-not-for-runtime';
const hash = (token: string) => createHash('sha256').update(token).digest('hex');
async function setup() {
  const f = await fixture(undefined, secret);
  const alice = await f.login('private-owner@example.com');
  const publicId = randomUUID(); const privateId = randomUUID();
  await f.request('PUT', '/profile', {name:'Alice',job:'Engineer',introduction:'Public introduction',
    contacts:[{id:publicId,kind:'email',label:'Public',value:'public@example.com'},
      {id:privateId,kind:'email',label:'Private',value:'hidden@example.com'},
      {id:randomUUID(),kind:'phone',label:'Private phone',value:'010-0000-0000'}], histories:[]}, alice.sessionToken);
  const card = (await f.request('POST', '/cards', {name:'Public card',description:'Selected fields',contactIds:[publicId],historyIds:[]}, alice.sessionToken)).body;
  const request = (method: 'GET'|'PUT'|'DELETE', path: string, token?: string, proxy = secret) => f.app.inject({method, url:`/v1/guest${path}`,
    headers:{'x-guest-proxy-key':proxy,...(token === undefined ? {} : {'x-guest-token':token})}});
  return {...f, alice, card, guest:request};
}

test('guest first save validates public ID, requires proxy, stores only digest, and isolates wallets', async () => {
  const f = await setup();
  try {
    assert.equal((await f.app.inject('/v1/guest/cards')).statusCode,403);
    assert.equal((await f.guest('GET','/cards',undefined,'wrong')).statusCode,403);
    assert.equal((await f.guest('GET','/cards')).statusCode,401);
    assert.equal((await f.guest('PUT','/cards/not-a-uuid')).statusCode,422);
    assert.equal((await f.guest('PUT',`/cards/${randomUUID()}`)).statusCode,404);
    assert.equal(await f.db.guestSession.count(),0);
    const saved = await f.guest('PUT',`/cards/${f.card.id}`);
    assert.equal(saved.statusCode,201);
    const token = saved.json().guestToken;
    assert.match(token,/^[A-Za-z0-9_-]{43}$/);
    assert.equal(saved.headers['cache-control'],'no-store');
    assert.equal(saved.headers['set-cookie'],undefined); // Next owns the secure cookie.
    assert.deepEqual(await f.db.guestSession.findMany(),[{digest:hash(token)}]);
    assert.deepEqual(await f.db.guestCard.findMany({select:{sessionDigest:true,cardId:true}}),[{sessionDigest:hash(token),cardId:f.card.id}]);
    assert.equal((await f.request('GET',`/cards/${f.card.id.toUpperCase()}`)).status,200);
    const duplicate = await f.guest('PUT',`/cards/${f.card.id.toUpperCase()}`,token);
    assert.equal(duplicate.statusCode,200);
    assert.deepEqual(duplicate.json(),{cardId:f.card.id,status:'alreadySaved'});
    const second = (await f.guest('PUT',`/cards/${f.card.id}`)).json().guestToken;
    assert.notEqual(second,token);
    await f.guest('DELETE',`/cards/${f.card.id}`,token);
    assert.deepEqual((await f.guest('GET','/cards',token)).json(),{items:[],shares:[]});
    const list = await f.guest('GET','/cards',second);
    assert.deepEqual(list.json(),{items:[f.card],shares:[]});
    assert.ok(!list.body.includes('hidden@example.com'));
    assert.ok(!list.body.includes('private-owner@example.com'));
    assert.ok(!list.body.includes(second));
    assert.equal((await f.guest('GET','/cards',f.card.id)).statusCode,401);
    assert.equal((await f.app.inject({url:'/v1/profile',headers:{'x-guest-token':second}})).statusCode,401);
    assert.equal((await f.app.inject({url:'/v1/wallet',headers:{'x-guest-token':second}})).statusCode,401);
    assert.equal((await f.app.inject({url:'/v1/guest/cards',headers:{'x-guest-proxy-key':secret,cookie:`__Host-dearby_guest=${second}`}})).statusCode,401);
    assert.equal((await f.guest('DELETE','/session',second)).statusCode,204);
    assert.equal((await f.guest('GET','/cards',second)).statusCode,401);
    assert.equal((await f.guest('PUT',`/cards/${f.card.id}`,second)).statusCode,401);
    assert.equal(await f.db.guestCard.count(),0);
  } finally {await f.close();}
});

test('same guest token survives cookie renewal, arbitrary elapsed time and database reopen', async () => {
  const f = await setup();
  const token = (await f.guest('PUT',`/cards/${f.card.id}`)).json().guestToken;
  f.advance(10 * 366 * 86400000);
  assert.deepEqual((await f.guest('GET','/cards',token)).json(),{items:[f.card],shares:[]});
  assert.deepEqual((await f.guest('PUT',`/cards/${f.card.id}`,token)).json(),{cardId:f.card.id,status:'alreadySaved'});
  assert.equal(await f.db.guestSession.count(),1);
  await f.app.close(); await f.db.$disconnect();
  const db = openPostgres({DATABASE_URL:f.connection});
  const {app} = createApp(db,{otpSecret:'test-only-secret-not-for-runtime',guestProxySecret:secret,sendCode:async()=>{throw Error('No SMTP');}});
  try {
    const response = await app.inject({url:'/v1/guest/cards',headers:{'x-guest-proxy-key':secret,'x-guest-token':token}});
    assert.deepEqual(response.json(),{items:[f.card],shares:[]});
    assert.deepEqual(await db.guestSession.findMany(),[{digest:hash(token)}]);
  } finally {await app.close();await db.$disconnect();await f.close();}
});

test('withdrawal, per-session capacity, idempotent removal and transaction failure preserve boundaries', async () => {
  const f = await setup();
  try {
    const token = (await f.guest('PUT',`/cards/${f.card.id}`)).json().guestToken;
    const cards = Array.from({length:100},()=>({...f.card,id:randomUUID()}));
    await f.db.card.createMany({data:cards.map(c=>({id:c.id,ownerId:f.alice.profileId,data:JSON.stringify(c)}))});
    for(const c of cards.slice(0,99)) assert.equal((await f.guest('PUT',`/cards/${c.id}`,token)).statusCode,200);
    const full = await f.guest('PUT',`/cards/${cards[99].id}`,token);
    assert.equal(full.statusCode,409);assert.equal(full.json().error.code,'GUEST_CAPACITY_EXCEEDED');
    assert.equal((await f.guest('PUT',`/cards/${f.card.id}`,token)).json().status,'alreadySaved');
    await f.request('DELETE',`/cards/${f.card.id}`,undefined,f.alice.sessionToken);
    assert.equal((await f.request('GET',`/cards/${f.card.id}`)).status,404);
    assert.equal((await f.guest('PUT',`/cards/${f.card.id}`,token)).statusCode,404);
    assert.equal((await f.guest('PUT',`/cards/${f.card.id}`)).statusCode,404);
    assert.equal((await f.guest('GET','/cards',token)).json().items.length,99);
    assert.equal((await f.guest('PUT',`/cards/${cards[99].id}`,token)).statusCode,200);
    assert.equal((await f.guest('DELETE',`/cards/${f.card.id}`,token)).statusCode,204);
    assert.equal((await f.guest('DELETE',`/cards/${randomUUID()}`,token)).statusCode,204);
    await f.admin.query("CREATE FUNCTION dearby_api.fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'private DB details'; END $$; CREATE TRIGGER fail_guest BEFORE INSERT ON dearby_api.guest_cards FOR EACH ROW EXECUTE FUNCTION dearby_api.fail()");
    const failure = await f.guest('PUT',`/cards/${cards[0].id}`);
    assert.equal(failure.statusCode,500);assert.ok(!failure.body.includes('private DB details'));
    assert.equal(await f.db.guestSession.count(),1);
    assert.equal(await f.db.guestCard.count(),100);
  } finally {await f.close();}
});

test('global capacity blocks new sessions only; secret absence fails closed; header spoof cannot bypass rate limits', async () => {
  const f = await setup();
  try {
    const token = (await f.guest('PUT',`/cards/${f.card.id}`)).json().guestToken;
    await f.db.guestSession.createMany({data:Array.from({length:9999},(_,i)=>({digest:i.toString(16).padStart(64,'0')}))});
    assert.equal((await f.guest('PUT',`/cards/${f.card.id}`)).statusCode,409);
    assert.equal((await f.guest('GET','/cards',token)).statusCode,200);
    assert.equal((await f.guest('PUT',`/cards/${f.card.id}`,token)).json().status,'alreadySaved');
    for(let i=0;i<118;i++) {
      const r=await f.app.inject({url:'/v1/guest/cards',headers:{'x-guest-proxy-key':secret,'x-guest-token':token,'x-forwarded-for':`192.0.2.${i}`}});
      assert.equal(r.statusCode,200);
    }
    assert.equal((await f.guest('GET','/cards',token)).statusCode,429);
    f.advance(60000);
    assert.equal((await f.guest('GET','/cards',token)).statusCode,200);
    const {app} = createApp(f.db,{otpSecret:'test-only-secret-not-for-runtime',sendCode:async()=>{}});
    try {assert.equal((await app.inject('/v1/guest/cards')).statusCode,503);} finally {await app.close();}
  } finally {await f.close();}
});

test('session creation throttle is independent of lifetime; concurrent saves remain unique', async () => {
  const f = await setup();
  try {
    let token = '';
    for (let i=0;i<60;i++) {
      const result = await f.guest('PUT',`/cards/${f.card.id}`);
      assert.equal(result.statusCode,201);token=result.json().guestToken;
    }
    assert.equal((await f.guest('PUT',`/cards/${f.card.id}`)).statusCode,429);
    assert.equal(await f.db.guestSession.count(),60);
    f.advance(60000);
    await f.guest('DELETE',`/cards/${f.card.id}`,token);
    const results = await Promise.all([f.guest('PUT',`/cards/${f.card.id}`,token),f.guest('PUT',`/cards/${f.card.id}`,token)]);
    assert.deepEqual(results.map(r=>r.json().status).sort(),['alreadySaved','saved']);
    assert.equal(await f.db.guestCard.count({where:{sessionDigest:hash(token)}}),1);
    assert.equal((await f.guest('PUT',`/cards/${f.card.id}`)).statusCode,201);
  } finally {await f.close();}
});

test('global abuse bound covers invalid tokens and read failure is never an empty wallet', async () => {
  const f = await setup();
  try {
    for (let i=0;i<1200;i++) assert.equal((await f.guest('GET','/cards','invalid-token')).statusCode,401);
    assert.equal((await f.guest('GET','/cards','another-invalid-token')).statusCode,429);
    assert.equal(await f.db.guestSession.count(),0);
    f.advance(60000);
    const token=(await f.guest('PUT',`/cards/${f.card.id}`)).json().guestToken;
    await f.admin.query('DROP TABLE dearby_api.guest_cards CASCADE');
    const failure=await f.guest('GET','/cards',token);
    assert.equal(failure.statusCode,500);
    assert.deepEqual(failure.json(),{error:{code:'INTERNAL_ERROR',message:'Request failed'}});
  } finally {await f.close();}
});
