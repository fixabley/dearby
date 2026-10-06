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
  assert.equal((await f.request('PUT','/profile',{...profile,name:'Changed',contacts:[{...contact,value:'changed@example.com'},secret]},alice.sessionToken)).status,200);
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
  const required = [{id:randomUUID(),kind:'phone',label:'',value:'010-1234-5678'},{id:randomUUID(),kind:'email',label:'',value:'a@example.com'}];
  const input = {name:'A',job:'',introduction:'',contacts:required,histories:[]};
  for (const body of [
    {...input,ownerId:randomUUID()},
    {...input,contacts:[...required,{id:randomUUID(),kind:'github',label:'x',value:'javascript:alert(1)'}]},
    {...input,contacts:[...required,{id:randomUUID(),kind:'github',label:'x',value:' javascript:alert(1) '}]},
    {...input,contacts:[...required,{id:randomUUID(),kind:'github',label:'x',value:'java\nscript:alert(1)'}]},
    {...input,histories:[{id:randomUUID(),title:'x',role:'',description:'',startDate:'2026-02-30',endDate:null}]},
  ]) assert.equal((await f.request('PUT','/profile',body,sessionToken)).status,422);
  assert.deepEqual((await f.request('GET','/profile',undefined,sessionToken)).body,initial);
  assert.equal((await f.request('GET','/cards/not-a-uuid')).status,422);
  assert.equal((await f.request('GET',`/cards/${randomUUID()}`)).status,404);
});

test('HTTP profile save requires phone and email with valid formats and ordered history dates; stored profiles still read', async t => {
  const f = await fixture(); t.after(f.close);
  const {sessionToken,profileId} = await f.login('required@example.com');
  const save = (body: unknown) => f.request('PUT','/profile',body,sessionToken);
  const contact = (kind: string, value: string) => ({id:randomUUID(),kind,label:'',value});
  const phone = contact('phone','+82 10-1234-5678'), email = contact('email','required@example.com');
  const base = {name:'R',job:'',introduction:'',contacts:[phone,email],histories:[]};
  const history = (startDate: unknown, endDate: unknown) => ({id:randomUUID(),title:'Activity',role:'',description:'',startDate,endDate});
  for (const contacts of [[],[phone],[email],[email,contact('kakao','id')],[contact('phone','010-12'),email],[contact('phone','0101234567890123'),email],
    [contact('phone','010.1234.5678'),email],[contact('phone','tel:01012345678'),email],[phone,contact('email','not-an-email')],[phone,contact('email','a@b')]]) {
    const r = await save({...base,contacts});
    assert.deepEqual([r.status,r.body],[422,{error:{code:'INVALID_INPUT',message:'Invalid contacts'}}],JSON.stringify(contacts));
  }
  for (const histories of [[history(undefined,null)],[history(null,null)],[history('2026-03',null)],[history('2026-03-01','2026-02-28')],[history('2026-03-01','2026-13-01')]]) {
    const r = await save({...base,histories});
    assert.deepEqual([r.status,r.body],[422,{error:{code:'INVALID_INPUT',message:'Invalid request'}}],JSON.stringify(histories));
  }
  for (const contacts of [[phone,email],[contact('phone','01012345678'),contact('phone','010 1234 5678'),email,contact('github','https://github.com/x')],[contact('phone','12345678'),contact('email','a.b+c@sub.example.co.kr')]])
    assert.equal((await save({...base,contacts})).status,200,JSON.stringify(contacts));
  const ok = await save({...base,histories:[history('2026-03-01',null),history('2026-03-01','2026-03-01'),history('2026-03-01','2026-06-30')]});
  assert.equal(ok.status,200);assert.equal(ok.body.histories.length,3);
  // A profile stored before the rule (no phone) is still returned as stored.
  const legacy = {id:profileId,name:'Legacy',job:'',introduction:'',contacts:[email],histories:[],updatedAt:'2026-10-01T00:00:00.000Z'};
  await f.db.profile.update({where:{id:profileId},data:{data:JSON.stringify(legacy)}});
  assert.deepEqual((await f.request('GET','/profile',undefined,sessionToken)).body,legacy);
  const card = await f.request('POST','/cards',{name:'Legacy card',description:'',contactIds:[email.id],historyIds:[]},sessionToken);
  assert.equal(card.status,201);
});
