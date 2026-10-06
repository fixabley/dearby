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
