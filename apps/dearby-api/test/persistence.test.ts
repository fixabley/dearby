import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { fixture } from './helpers.js';
import { openDatabase } from '../src/database.js';
import { createApp } from '../src/app.js';
import { smtpMailer } from '../src/mail.js';

test('HTTP sessions/profile/wallet/revocation/idempotency and quotas survive database reopen', async () => {
  const f = await fixture();
  const alice = await f.login('alice@example.com');
  const bob = await f.login('bob@example.com');
  await f.request('PUT','/profile',{name:'Persistent Alice',job:'Engineer',introduction:'',contacts:[],histories:[]},alice.sessionToken);
  const card = (await f.request('POST','/cards',{name:'Persistent card',description:'',contactIds:[],historyIds:[]},alice.sessionToken)).body;
  const input = {cardId:card.id,recipientProfileId:bob.profileId,context:{activityId:null,label:'Meetup'},requestId:randomUUID()};
  const sent = await f.request('POST','/exchanges',input,alice.sessionToken);
  const revoked = (await f.request('POST','/cards',{name:'Withdrawn',description:'',contactIds:[],historyIds:[]},alice.sessionToken)).body;
  await f.request('DELETE',`/cards/${revoked.id}`,undefined,alice.sessionToken);
  await f.app.close(); f.db.close();
  const db = openDatabase(f.path);
  const {app} = createApp(db,{otpSecret:'test-only-secret-not-used-outside-tests',sendCode:smtpMailer({})});
  try {
    await app.listen({host:'127.0.0.1',port:0});
    const address = app.server.address();
    if (!address || typeof address === 'string') throw new Error('No listener');
    const request = async (method:string, path:string, token:string, body?:unknown) => {
      const response = await fetch(`http://127.0.0.1:${address.port}/v1${path}`,{method,headers:{authorization:`Bearer ${token}`,...(body === undefined ? {} : {'content-type':'application/json'})},body:body === undefined ? undefined : JSON.stringify(body)});
      const text = await response.text(); return {status:response.status,body:text ? JSON.parse(text) : null};
    };
    assert.equal((await request('GET','/profile',alice.sessionToken)).body.name,'Persistent Alice');
    assert.equal((await request('GET','/wallet',bob.sessionToken)).body.items.length,1);
    assert.deepEqual((await request('POST','/exchanges',alice.sessionToken,input)).body,sent.body);
    assert.equal((await request('GET',`/cards/${revoked.id}`,alice.sessionToken)).status,404);
    assert.equal((await request('POST','/auth/challenges',alice.sessionToken,{email:'alice@example.com'})).status,429);
    assert.equal((db.prepare('SELECT COUNT(*) AS n FROM migrations').get() as {n:number}).n,3);
    assert.equal((db.prepare('SELECT COUNT(*) AS n FROM receipts').get() as {n:number}).n,1);
    assert.equal((await request('DELETE','/auth/session',alice.sessionToken)).status,204);
    assert.equal((await request('GET','/profile',alice.sessionToken)).status,401);
  } finally { await app.close(); db.close(); await f.close(); }
});
