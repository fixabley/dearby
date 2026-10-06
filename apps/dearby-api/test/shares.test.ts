import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fixture } from './helpers.js';
import { createApp } from '../src/app.js';
import { ApiError } from '../src/validation.js';

const secret = 'test-only-guest-proxy-secret-not-for-runtime';
const hash = (token: string) => createHash('sha256').update(token).digest('hex');
const data = JSON.parse(readFileSync(new URL('./fixtures/card-shares.json', import.meta.url), 'utf8'));
async function setup() {
  const f = await fixture(undefined, secret);
  const guest = (method: 'GET'|'PUT'|'DELETE', path: string, token?: string) => f.app.inject({method, url:`/v1/guest${path}`,
    headers:{'x-guest-proxy-key':secret,...(token === undefined ? {} : {'x-guest-token':token})}});
  const publish = async (activities: {id: string; title: string; status?: string}[]) => {
    const organization = randomUUID(), program = randomUUID();
    await f.admin.query('INSERT INTO public.catalog_organizations(id,name) VALUES($1,$2)',[organization,'Fixture']);
    await f.admin.query('INSERT INTO public.catalog_programs(id,organization_id,title) VALUES($1,$2,$3)',[program,organization,'Fixture']);
    for (const a of activities) await f.admin.query('INSERT INTO public.catalog_activities(id,program_id,organization_id,title,official_url,publication_status) VALUES($1,$2,$3,$4,$5,$6)',
      [a.id,program,organization,a.title,'https://example.com/fixture',a.status ?? 'published']);
  };
  return {...f, guest, publish};
}

test('web fixture: shares with several activities, guest links and a withdrawn card match the real API', async () => {
  const f = await setup();
  try {
    await f.db.profile.create({data:{id:data.ownerId,email:'fixture-owner@example.com',data:'{}'}});
    await f.db.card.createMany({data:data.cards.map((c: {id: string}) => ({id:c.id,ownerId:data.ownerId,data:JSON.stringify(c)}))});
    for (const s of data.shares) await f.db.cardShare.create({data:{id:s.id,cardId:s.cardId,activities:JSON.stringify(s.activities),createdAt:s.createdAt}});
    const card = (cardId: string) => data.cards.find((c: {id: string}) => c.id === cardId);
    for (const s of data.shares) assert.deepEqual((await f.request('GET',`/shares/${s.id.toUpperCase()}`)).body,{share:s,card:card(s.cardId)});
    let token: string | undefined;
    for (const save of data.guestSaves) {
      f.setClock(Date.parse(save.savedAt));
      const response = await f.guest('PUT',`/shares/${save.shareId}`,token);
      const {guestToken, ...body} = response.json();
      token ??= guestToken;
      assert.deepEqual({status:response.statusCode,body},save.response);
    }
    assert.equal((await f.guest('GET','/cards',token)).json().shares.length,5);
    await f.db.card.update({where:{id:data.revokedCardId},data:{revoked:true}});
    const revokedShare = data.shares.find((s: {cardId: string}) => s.cardId === data.revokedCardId).id;
    const expected = data.afterRevocation;
    const revoked = await f.request('GET',`/shares/${revokedShare}`);
    assert.deepEqual({status:revoked.status,body:revoked.body},expected.getRevokedShare);
    assert.equal((await f.guest('PUT',`/shares/${revokedShare}`,token)).statusCode,404);
    const list = await f.guest('GET','/cards',token);
    assert.deepEqual({status:list.statusCode,body:list.json()},{...expected.guestCards,body:{...expected.guestCards.body,items:expected.guestCards.body.items.map(card)}});
  } finally {await f.close();}
});

test('owner share creation validates ownership, body and current catalog; snapshots ignore later catalog edits', async () => {
  const f = await setup();
  try {
    const alice = await f.login('alice@example.com');
    const bob = await f.login('bob@example.com');
    const card = (await f.request('POST','/cards',{name:'Shared',description:'',contactIds:[],historyIds:[]},alice.sessionToken)).body;
    const [first, second, draft] = [randomUUID(), randomUUID(), randomUUID()];
    await f.publish([{id:first,title:'First activity'},{id:second,title:'Second activity'},{id:draft,title:'Draft',status:'draft'}]);
    const ids = Array.from({length:11},()=>randomUUID());
    assert.equal((await f.request('POST',`/cards/${card.id}/shares`,{activityIds:[]})).status,401);
    for (const body of [{},{activityIds:[first],extra:true},{activityIds:['not-a-uuid']},{activityIds:[first,first.toUpperCase()]},{activityIds:ids}])
      assert.equal((await f.request('POST',`/cards/${card.id}/shares`,body,alice.sessionToken)).status,422);
    assert.equal((await f.request('POST',`/cards/${randomUUID()}/shares`,{activityIds:[]},alice.sessionToken)).status,404);
    assert.equal((await f.request('POST',`/cards/${card.id}/shares`,{activityIds:[]},bob.sessionToken)).status,403);
    for (const missing of [randomUUID(),draft]) {
      const r = await f.request('POST',`/cards/${card.id}/shares`,{activityIds:[first,missing]},alice.sessionToken);
      assert.equal(r.status,422);assert.equal(r.body.error.code,'INVALID_SELECTION');
    }
    assert.equal(await f.db.cardShare.count(),0);
    const created = await f.request('POST',`/cards/${card.id.toUpperCase()}/shares`,{activityIds:[second.toUpperCase(),first]},alice.sessionToken);
    assert.equal(created.status,201);
    assert.deepEqual(created.body.activities,[{id:second,title:'Second activity'},{id:first,title:'First activity'}]);
    assert.equal(created.body.cardId,card.id);
    await f.admin.query("UPDATE public.catalog_activities SET title='Renamed' WHERE id=$1",[first]);
    assert.deepEqual((await f.request('GET',`/shares/${created.body.id}`)).body,{share:created.body,card});
    const empty = await f.request('POST',`/cards/${card.id}/shares`,{activityIds:[]},alice.sessionToken);
    assert.equal(empty.status,201);assert.deepEqual(empty.body.activities,[]);
    assert.equal((await f.request('GET',`/shares/${randomUUID()}`)).status,404);
    assert.equal((await f.request('GET','/shares/not-a-uuid')).status,422);
    // Catalog failure is a dependency error, never a partial snapshot; an empty selection needs no catalog.
    const {app} = createApp(f.db,{otpSecret:'test-only-secret-not-used-outside-tests',sendCode:async()=>{},catalogReader:async()=>{throw new ApiError(503,'CATALOG_UNAVAILABLE','Activity catalog temporarily unavailable');}});
    try {
      const post = (activityIds: string[]) => app.inject({method:'POST',url:`/v1/cards/${card.id}/shares`,headers:{authorization:`Bearer ${alice.sessionToken}`},payload:{activityIds}});
      assert.equal((await post([first])).statusCode,503);
      assert.equal((await post([])).statusCode,201);
    } finally {await app.close();}
    // The existing card routes are unchanged by shares.
    assert.deepEqual((await f.request('GET',`/cards/${card.id}`)).body,card);
    assert.equal((await f.request('DELETE',`/cards/${card.id}`,undefined,alice.sessionToken)).status,204);
    assert.equal((await f.request('GET',`/shares/${created.body.id}`)).status,404);
    assert.equal((await f.request('POST',`/cards/${card.id}/shares`,{activityIds:[]},alice.sessionToken)).status,404);
    assert.equal(await f.db.cardShare.count(),3); // Withdrawal keeps records; it only blocks public reads.
  } finally {await f.close();}
});

test('guest share saves keep one card per session, cap links per card, cascade deletes and share the guest limits', async () => {
  const f = await setup();
  try {
    const alice = await f.login('alice@example.com');
    const card = (await f.request('POST','/cards',{name:'Shared',description:'',contactIds:[],historyIds:[]},alice.sessionToken)).body;
    const other = (await f.request('POST','/cards',{name:'Plain',description:'',contactIds:[],historyIds:[]},alice.sessionToken)).body;
    const shares = Array.from({length:21},(_,i)=>({id:randomUUID(),cardId:card.id,activities:'[]',createdAt:new Date(i).toISOString()}));
    await f.db.cardShare.createMany({data:shares});
    assert.equal((await f.app.inject({method:'PUT',url:`/v1/guest/shares/${shares[0].id}`})).statusCode,403);
    assert.equal((await f.guest('PUT','/shares/not-a-uuid')).statusCode,422);
    assert.equal((await f.guest('PUT',`/shares/${randomUUID()}`)).statusCode,404);
    assert.equal((await f.guest('PUT',`/shares/${shares[0].id}`,'invalid-token')).statusCode,401);
    assert.equal(await f.db.guestSession.count(),0);
    const first = await f.guest('PUT',`/shares/${shares[0].id.toUpperCase()}`);
    assert.equal(first.statusCode,201);assert.equal(first.headers['cache-control'],'no-store');
    const token = first.json().guestToken;
    assert.deepEqual(first.json(),{cardId:card.id,shareId:shares[0].id,status:'saved',guestToken:token});
    // Saving the card directly afterwards keeps the existing card and its share link.
    assert.deepEqual((await f.guest('PUT',`/cards/${card.id}`,token)).json(),{cardId:card.id,status:'alreadySaved'});
    assert.deepEqual((await f.guest('PUT',`/cards/${other.id}`,token)).json(),{cardId:other.id,status:'saved'});
    for (const s of shares.slice(1,20)) assert.equal((await f.guest('PUT',`/shares/${s.id}`,token)).json().status,'saved');
    const full = await f.guest('PUT',`/shares/${shares[20].id}`,token);
    assert.equal(full.statusCode,409);assert.equal(full.json().error.code,'GUEST_CAPACITY_EXCEEDED');
    assert.deepEqual((await f.guest('PUT',`/shares/${shares[5].id}`,token)).json(),{cardId:card.id,shareId:shares[5].id,status:'alreadySaved'});
    const list = (await f.guest('GET','/cards',token)).json();
    assert.deepEqual(list.items,[card,other]);
    assert.deepEqual(list.shares.map((s: {shareId: string}) => s.shareId),shares.slice(0,20).map(s=>s.id));
    // Storage holds only the digest, card and share IDs plus server save time.
    const row = await f.db.guestCardShare.findFirstOrThrow({where:{shareId:shares[0].id}});
    assert.deepEqual(Object.keys(row).sort(),['cardId','ordinal','savedAt','sessionDigest','shareId']);
    assert.equal(row.sessionDigest,hash(token));
    // Removing the saved card removes its links; a new share save starts fresh.
    assert.equal((await f.guest('DELETE',`/cards/${card.id}`,token)).statusCode,204);
    assert.equal(await f.db.guestCardShare.count(),0);
    assert.deepEqual((await f.guest('GET','/cards',token)).json(),{items:[other],shares:[]});
    assert.equal((await f.guest('PUT',`/shares/${shares[20].id}`,token)).json().status,'saved');
    // A second session saving the same share is independent.
    const second = (await f.guest('PUT',`/shares/${shares[20].id}`)).json().guestToken;
    assert.equal(await f.db.guestCardShare.count(),2);
    assert.equal((await f.guest('DELETE','/session',second)).statusCode,204);
    assert.equal(await f.db.guestCardShare.count({where:{sessionDigest:hash(second)}}),0);
    assert.equal(await f.db.guestCardShare.count(),1);
    // Requests to the share route count towards the same per-session limit as card routes.
    f.advance(60000);
    for (let i=0;i<120;i++) assert.equal((await f.guest('GET','/cards',token)).statusCode,200);
    assert.equal((await f.guest('PUT',`/shares/${shares[0].id}`,token)).statusCode,429);
    assert.equal(await f.db.cardShare.count(),21);
  } finally {await f.close();}
});

test('share saves respect the 100 card capacity and roll back the session on storage failure', async () => {
  const f = await setup();
  try {
    const alice = await f.login('alice@example.com');
    const card = (await f.request('POST','/cards',{name:'Shared',description:'',contactIds:[],historyIds:[]},alice.sessionToken)).body;
    const cards = Array.from({length:100},()=>({...card,id:randomUUID()}));
    await f.db.card.createMany({data:cards.map(c=>({id:c.id,ownerId:alice.profileId,data:JSON.stringify(c)}))});
    const share = {id:randomUUID(),cardId:card.id,activities:'[]',createdAt:new Date(0).toISOString()};
    await f.db.cardShare.create({data:share});
    const token = (await f.guest('PUT',`/cards/${cards[0].id}`)).json().guestToken;
    for (const c of cards.slice(1)) assert.equal((await f.guest('PUT',`/cards/${c.id}`,token)).statusCode,200);
    const full = await f.guest('PUT',`/shares/${share.id}`,token);
    assert.equal(full.statusCode,409);assert.equal(await f.db.guestCard.count(),100);
    await f.admin.query("CREATE FUNCTION dearby_api.fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'private DB details'; END $$; CREATE TRIGGER fail_link BEFORE INSERT ON dearby_api.guest_card_shares FOR EACH ROW EXECUTE FUNCTION dearby_api.fail()");
    const failure = await f.guest('PUT',`/shares/${share.id}`);
    assert.equal(failure.statusCode,500);assert.ok(!failure.body.includes('private DB details'));
    assert.equal(await f.db.guestSession.count(),1);
    assert.equal(await f.db.guestCard.count(),100);
  } finally {await f.close();}
});
