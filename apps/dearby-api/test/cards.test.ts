import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { fixture } from './helpers.js';

test('HTTP profile authorization, explicit public projection, immutable snapshots and revocation', async t => {
  const f = await fixture(); t.after(f.close);
  const alice = await f.login('alice@example.com');
  const bob = await f.login('bob@example.com');
  const contact = {id:randomUUID(),kind:'email',label:'Work',value:'public@example.com'};
  const secret = {id:randomUUID(),kind:'phone',label:'Private',value:'010-1234-5678'};
  const history = {id:randomUUID(),title:'Activity',role:'Member',startDate:'2026-01-01',endDate:null,description:'Public history'};
  const profile = {name:'Alice',job:'Engineer',introduction:'Hello',contacts:[contact,secret],histories:[history]};
  assert.equal((await f.request('PUT','/profile',profile)).status,401);
  assert.equal((await f.request('GET','/profile')).status,401);
  const saved = await f.request('PUT','/profile',profile,alice.sessionToken);
  assert.equal(saved.status,200); assert.equal(saved.body.id,alice.profileId);
  assert.equal((await f.request('GET','/profile',undefined,bob.sessionToken)).body.name,'');
  const cardInput = {name:'Conference card',description:'For work',contactIds:[contact.id],historyIds:[]};
  assert.equal((await f.request('POST','/cards',cardInput)).status,401);
  assert.equal((await f.request('POST','/cards',cardInput,bob.sessionToken)).status,422);
  const created = await f.request('POST','/cards',cardInput,alice.sessionToken);
  assert.equal(created.status,201);
  const card = created.body;
  const publicRead = await f.request('GET',`/cards/${card.id}`);
  assert.equal(publicRead.status,200); assert.deepEqual(publicRead.body.contacts,[contact]);
  assert.deepEqual(publicRead.body.histories,[]); assert.equal(publicRead.body.profileName,'Alice');
  assert.equal(JSON.stringify(publicRead.body).includes(secret.value),false);
  assert.equal(JSON.stringify(publicRead.body).includes('alice@example.com'),false);
  await f.request('PUT','/profile',{...profile,name:'Changed',contacts:[]},alice.sessionToken);
  assert.deepEqual((await f.request('GET',`/cards/${card.id}`)).body,card);
  assert.equal((await f.request('GET','/cards',undefined,bob.sessionToken)).body.items.length,0);
  assert.equal((await f.request('DELETE',`/cards/${card.id}`,undefined,bob.sessionToken)).status,403);
  assert.equal((await f.request('DELETE',`/cards/${card.id}`,undefined,alice.sessionToken)).status,204);
  assert.equal((await f.request('GET',`/cards/${card.id}`)).status,404);
  assert.equal((await f.request('GET','/cards',undefined,alice.sessionToken)).body.items.length,0);
});

test('HTTP validation rejects dangerous schemes, duplicate fields and invalid dates without overwriting profile', async t => {
  const f = await fixture(); t.after(f.close);
  const {sessionToken} = await f.login('a@example.com');
  const initial = (await f.request('GET','/profile',undefined,sessionToken)).body;
  const input = {name:'A',job:'',introduction:'',contacts:[],histories:[]};
  for (const body of [
    {...input,ownerId:randomUUID()},
    {...input,contacts:[{id:randomUUID(),kind:'github',label:'x',value:'javascript:alert(1)'}]},
    {...input,contacts:[{id:randomUUID(),kind:'github',label:'x',value:' javascript:alert(1) '}]},
    {...input,contacts:[{id:randomUUID(),kind:'github',label:'x',value:'java\nscript:alert(1)'}]},
    {...input,histories:[{id:randomUUID(),title:'x',role:'',description:'',startDate:'2026-02-30',endDate:null}]},
  ]) assert.equal((await f.request('PUT','/profile',body,sessionToken)).status,422);
  assert.deepEqual((await f.request('GET','/profile',undefined,sessionToken)).body,initial);
  assert.equal((await f.request('GET','/cards/not-a-uuid')).status,422);
  assert.equal((await f.request('GET',`/cards/${randomUUID()}`)).status,404);
});
