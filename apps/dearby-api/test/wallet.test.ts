import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { fixture } from './helpers.js';
const emptyContext = {activityId:null,label:null};
async function setup() {
  const f = await fixture();
  const alice = await f.login('alice@example.com');
  const bob = await f.login('bob@example.com');
  const charlie = await f.login('charlie@example.com');
  const makeCard = async (token:string) => (await f.request('POST','/cards',{name:'Card',description:'',contactIds:[],historyIds:[]},token)).body;
  return {...f,alice,bob,charlie,makeCard,cardA:await makeCard(alice.sessionToken),cardB:await makeCard(bob.sessionToken)};
}

test('HTTP imports preserve per-item outcomes, duplicate prevention and original saved context/date', async t => {
  const f = await setup(); t.after(f.close);
  const revoked = await f.makeCard(f.alice.sessionToken);
  await f.request('DELETE',`/cards/${revoked.id}`,undefined,f.alice.sessionToken);
  const item = {cardId:f.cardA.id,context:{activityId:null,label:'Conference'},savedAt:'2026-09-01T10:00:00Z'};
  const missing = {...item,cardId:randomUUID()};
  const items = [item,missing,{...item,cardId:revoked.id},{...item,cardId:f.cardB.id,savedAt:'bad'}];
  assert.equal((await f.request('POST','/wallet/import',{items})).status,401);
  const result = await f.request('POST','/wallet/import',{items},f.bob.sessionToken);
  assert.equal(result.status,200);
  assert.deepEqual(result.body.items.map((x:{status:string}) => x.status),['imported','failed','failed','failed']);
  assert.equal(result.body.items[1].receiptId,null);
  const repeat = await f.request('POST','/wallet/import',{items:[item,item]},f.bob.sessionToken);
  assert.deepEqual(repeat.body.items.map((x:{status:string}) => x.status),['alreadySaved','alreadySaved']);
  const wallet = (await f.request('GET','/wallet',undefined,f.bob.sessionToken)).body.items;
  assert.equal(wallet.length,1); assert.equal(wallet[0].receivedAt,item.savedAt);
  assert.deepEqual(wallet[0].context,item.context); assert.equal(wallet[0].reciprocal,false);
  assert.equal((await f.request('GET','/wallet',undefined,f.charlie.sessionToken)).body.items.length,0);
  assert.equal((await f.request('GET','/wallet')).status,401);
  const concurrent = await Promise.all(Array.from({length:4},() => f.request('POST','/wallet/import',{items:[item]},f.charlie.sessionToken)));
  assert.equal(concurrent.filter(r => r.body.items[0].status === 'imported').length,1);
  assert.equal((await f.request('GET','/wallet',undefined,f.charlie.sessionToken)).body.items.length,1);
});

test('HTTP direct exchange idempotency, mismatch, reciprocal detection and repeated history', async t => {
  const f = await setup(); t.after(f.close);
  const input = {cardId:f.cardA.id,recipientProfileId:f.bob.profileId,context:emptyContext,requestId:randomUUID()};
  assert.equal((await f.request('POST','/exchanges',input)).status,401);
  assert.equal((await f.request('POST','/exchanges',input,f.charlie.sessionToken)).status,403);
  assert.equal((await f.request('POST','/exchanges',{...input,recipientProfileId:randomUUID()},f.alice.sessionToken)).status,404);
  assert.equal((await f.request('POST','/exchanges',{...input,recipientProfileId:f.alice.profileId},f.alice.sessionToken)).status,422);
  assert.equal((await f.request('POST','/exchanges',{...input,context:{activityId:randomUUID(),label:'Ambiguous'}},f.alice.sessionToken)).status,422);
  const sent = await f.request('POST','/exchanges',input,f.alice.sessionToken);
  assert.equal(sent.status,201);
  const importedAfterDelivery = await f.request('POST','/wallet/import',{items:[{cardId:f.cardA.id,context:emptyContext,savedAt:'2026-09-01T00:00:00Z'}]},f.bob.sessionToken);
  assert.equal(importedAfterDelivery.body.items[0].status,'alreadySaved');
  const concurrent = await Promise.all(Array.from({length:4},() => f.request('POST','/exchanges',input,f.alice.sessionToken)));
  for (const r of concurrent) { assert.equal(r.status,201); assert.deepEqual(r.body,sent.body); }
  const reordered = {requestId:input.requestId,context:{label:null,activityId:null},recipientProfileId:input.recipientProfileId,cardId:input.cardId};
  assert.deepEqual((await f.request('POST','/exchanges',reordered,f.alice.sessionToken)).body,sent.body);
  assert.equal((await f.request('POST','/exchanges',{...input,context:{activityId:null,label:'Changed'}},f.alice.sessionToken)).status,409);
  let walletB = (await f.request('GET','/wallet',undefined,f.bob.sessionToken)).body.items;
  assert.equal(walletB.length,1); assert.equal(walletB[0].reciprocal,false);
  assert.equal((await f.request('POST','/exchanges',{cardId:f.cardB.id,recipientProfileId:f.alice.profileId,context:{activityId:randomUUID(),label:null},requestId:randomUUID()},f.bob.sessionToken)).status,201);
  walletB = (await f.request('GET','/wallet',undefined,f.bob.sessionToken)).body.items;
  assert.equal(walletB[0].reciprocal,true);
  assert.equal((await f.request('GET','/wallet',undefined,f.alice.sessionToken)).body.items[0].reciprocal,true);
  const again = await f.request('POST','/exchanges',{...input,requestId:randomUUID()},f.alice.sessionToken);
  assert.equal(again.status,201); assert.notEqual(again.body.receiptId,sent.body.receiptId);
  assert.equal((await f.request('GET','/wallet',undefined,f.bob.sessionToken)).body.items.length,2);
  await f.request('DELETE',`/cards/${f.cardA.id}`,undefined,f.alice.sessionToken);
  assert.equal((await f.request('POST','/exchanges',{...input,requestId:randomUUID()},f.alice.sessionToken)).status,404);
  assert.deepEqual((await f.request('POST','/exchanges',input,f.alice.sessionToken)).body,sent.body);
  // History remains durable but withdrawn contents are never newly returned.
  assert.equal((await f.request('GET',`/cards/${f.cardA.id}`)).status,404);
  assert.equal((await f.request('GET','/wallet',undefined,f.bob.sessionToken)).body.items.length,0);
  assert.equal((f.db.prepare('SELECT COUNT(*) AS n FROM receipts WHERE recipient_id = ?').get(f.bob.profileId) as {n:number}).n,2);
});

test('HTTP exchange SQL failure rolls back receipt and idempotency; import failures preserve siblings', async t => {
  const f = await setup(); t.after(f.close);
  const input = {cardId:f.cardA.id,recipientProfileId:f.bob.profileId,context:emptyContext,requestId:randomUUID()};
  f.db.exec("CREATE TRIGGER fail_exchange BEFORE INSERT ON exchanges BEGIN SELECT RAISE(ABORT, 'injected failure'); END");
  const failed = await f.request('POST','/exchanges',input,f.alice.sessionToken);
  assert.equal(failed.status,500); assert.equal(JSON.stringify(failed.body).includes('injected'),false);
  assert.equal((await f.request('GET','/wallet',undefined,f.bob.sessionToken)).body.items.length,0);
  assert.equal(f.db.prepare('SELECT * FROM exchanges').get(),undefined);
  f.db.exec('DROP TRIGGER fail_exchange');
  assert.equal((await f.request('POST','/exchanges',input,f.alice.sessionToken)).status,201);
  // Fixed UUIDs come from the API, not external SQL input.
  f.db.exec(`CREATE TRIGGER fail_import BEFORE INSERT ON receipts WHEN NEW.card_id = '${f.cardB.id}' BEGIN SELECT RAISE(ABORT, 'injected failure'); END`);
  const result = await f.request('POST','/wallet/import',{items:[f.cardA,f.cardB].map(card => ({cardId:card.id,context:emptyContext,savedAt:'2026-09-01T00:00:00Z'}))},f.charlie.sessionToken);
  assert.deepEqual(result.body.items.map((x:{status:string}) => x.status),['imported','failed']);
  assert.equal((await f.request('GET','/wallet',undefined,f.charlie.sessionToken)).body.items.length,1);
  f.db.exec('DROP TRIGGER fail_import');
  const retry = await f.request('POST','/wallet/import',{items:[{cardId:f.cardB.id,context:emptyContext,savedAt:'2026-09-01T00:00:00Z'}]},f.charlie.sessionToken);
  assert.equal(retry.body.items[0].status,'imported');
});
