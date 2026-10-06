import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { fixture } from './helpers.js';

test('wallet share links must reference a share of the stored card and keep receipts', async t => {
  const f = await fixture(); t.after(f.close);
  const owner = await f.login('owner@example.com');
  const reader = await f.login('reader@example.com');
  const card = (await f.request('POST','/cards',{name:'Shared',description:'',contactIds:[],historyIds:[]},owner.sessionToken)).body;
  const other = (await f.request('POST','/cards',{name:'Other',description:'',contactIds:[],historyIds:[]},owner.sessionToken)).body;
  const share = {id:randomUUID(),cardId:card.id,activities:'[]',createdAt:new Date(0).toISOString()};
  await f.db.cardShare.create({data:share});
  const receipt = await f.db.receipt.create({data:{id:randomUUID(),recipientId:reader.profileId,cardId:card.id,context:'{}',receivedAt:new Date(0).toISOString()}});
  await assert.rejects(f.db.walletShare.create({data:{receiptId:receipt.id,cardId:other.id,shareId:share.id,savedAt:'x'}}));
  await f.db.walletShare.create({data:{receiptId:receipt.id,cardId:card.id,shareId:share.id,savedAt:'x'}});
  await assert.rejects(f.db.walletShare.create({data:{receiptId:receipt.id,cardId:card.id,shareId:share.id,savedAt:'y'}}));
  await assert.rejects(f.db.receipt.delete({where:{id:receipt.id}}));
  assert.equal(await f.db.walletShare.count(),1);
});

async function setup() {
  const f = await fixture();
  const owner = await f.login('owner@example.com');
  const reader = await f.login('reader@example.com');
  const publish = async (name: string) => (await f.request('POST','/cards',{name,description:'',contactIds:[],historyIds:[]},owner.sessionToken)).body;
  const share = async (cardId: string, activities: {id: string; title: string}[] = []) => {
    const row = {id:randomUUID(),cardId,activities:JSON.stringify(activities),createdAt:new Date(0).toISOString()};
    await f.db.cardShare.create({data:row}); return row.id;
  };
  const save = (shareId: string, token = reader.sessionToken) => f.request('PUT',`/wallet/shares/${shareId}`,undefined,token);
  return {...f, owner, reader, publish, share, save};
}

test('HTTP member share save: first save creates the receipt, more shares link to it, repeats are alreadySaved', async t => {
  const f = await setup(); t.after(f.close);
  const card = await f.publish('Shared');
  const activity = {id:randomUUID(),title:'Conference'};
  const [first, second] = [await f.share(card.id,[activity]), await f.share(card.id)];
  assert.equal((await f.request('PUT',`/wallet/shares/${first}`)).status,401);
  assert.equal((await f.save('not-a-uuid')).status,422);
  assert.equal((await f.save(randomUUID())).status,404);
  f.setClock(Date.parse('2026-10-06T01:00:00.000Z'));
  const created = await f.save(first.toUpperCase());
  assert.equal(created.status,201);
  const receiptId = created.body.receiptId;
  assert.deepEqual(created.body,{receiptId,cardId:card.id,shareId:first,status:'saved'});
  f.advance(60000);
  assert.deepEqual([(await f.save(second)).status,(await f.save(second)).body.status],[200,'alreadySaved']);
  const repeat = await f.save(first);
  assert.deepEqual([repeat.status,repeat.body],[200,{receiptId,cardId:card.id,shareId:first,status:'alreadySaved'}]);
  const wallet = (await f.request('GET','/wallet',undefined,f.reader.sessionToken)).body;
  assert.deepEqual(wallet.items.map((i: {id: string; context: unknown; reciprocal: boolean}) => [i.id,i.context,i.reciprocal]),[[receiptId,{activityId:null,label:null},false]]);
  assert.deepEqual(wallet.shares,[
    {receiptId,cardId:card.id,shareId:first,activities:[activity],savedAt:'2026-10-06T01:00:00.000Z'},
    {receiptId,cardId:card.id,shareId:second,activities:[],savedAt:'2026-10-06T01:01:00.000Z'}]);
  // The existing import contract is unchanged: the card is already in the wallet.
  const imported = await f.request('POST','/wallet/import',{items:[{cardId:card.id,context:{activityId:null,label:'Later'},savedAt:'2026-10-07T00:00:00.000Z'}]},f.reader.sessionToken);
  assert.deepEqual(imported.body.items,[{cardId:card.id,status:'alreadySaved',receiptId}]);
  assert.equal(await f.db.receipt.count({where:{recipientId:f.reader.profileId}}),1);
});

test('HTTP member share save reuses an imported receipt, caps links per card, rejects own cards and hides withdrawn cards', async t => {
  const f = await setup(); t.after(f.close);
  const card = await f.publish('Capped');
  const imported = await f.request('POST','/wallet/import',{items:[{cardId:card.id,context:{activityId:null,label:'Met'},savedAt:'2026-10-01T00:00:00.000Z'}]},f.reader.sessionToken);
  const receiptId = imported.body.items[0].receiptId;
  const shares = [];
  for (let i=0;i<21;i++) shares.push(await f.share(card.id));
  const linked = await f.save(shares[0]);
  assert.deepEqual([linked.status,linked.body.receiptId,linked.body.status],[200,receiptId,'saved']);
  for (const id of shares.slice(1,20)) assert.equal((await f.save(id)).body.status,'saved');
  const full = await f.save(shares[20]);
  assert.deepEqual([full.status,full.body.error.code],[409,'WALLET_CAPACITY_EXCEEDED']);
  assert.deepEqual([(await f.save(shares[3])).status,(await f.save(shares[3])).body.status],[200,'alreadySaved']);
  const own = await f.save(shares[0],f.owner.sessionToken);
  assert.deepEqual([own.status,own.body.error.code],[422,'INVALID_RECIPIENT']);
  assert.equal(await f.db.receipt.count({where:{recipientId:f.owner.profileId}}),0);
  assert.equal((await f.request('DELETE',`/cards/${card.id}`,undefined,f.owner.sessionToken)).status,204);
  assert.equal((await f.save(shares[0])).status,404);
  assert.deepEqual((await f.request('GET','/wallet',undefined,f.reader.sessionToken)).body,{items:[],shares:[]});
  assert.equal(await f.db.walletShare.count(),20); // Withdrawal hides links; receipts and links are retained.
});
