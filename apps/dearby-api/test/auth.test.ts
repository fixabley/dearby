import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { fixture } from './helpers.js';
import { smtpMailer } from '../src/mail.js';

test('HTTP OTP never returned, is hashed, one use; session expires and revokes', async t => {
  const f = await fixture(); t.after(f.close);
  const challenge = await f.request('POST','/auth/challenges',{email:'owner@example.com'});
  assert.equal(challenge.status,202);
  assert.deepEqual(Object.keys(challenge.body).sort(),['challengeId','expiresAt']);
  const code = f.codes.get('owner@example.com')!;
  const stored = (await f.db.challenge.findFirstOrThrow());
  assert.equal(stored.digest.length,64); assert.notEqual(stored.digest,code);
  const input = {challengeId:challenge.body.challengeId,code};
  const session = await f.request('POST','/auth/sessions',input);
  assert.equal(session.status,200); assert.equal(session.headers.get('cache-control'),'no-store');
  assert.equal((await f.request('POST','/auth/sessions',input)).status,401);
  const token = session.body.sessionToken;
  assert.equal(await f.db.session.findUnique({where:{digest:token}}),null);
  assert.equal((await f.request('DELETE','/auth/session',undefined,token)).status,204);
  assert.equal((await f.request('DELETE','/auth/session',undefined,token)).status,401);
  f.advance(61000);
  const next = await f.login('owner@example.com');
  assert.equal(next.profileId,session.body.profileId);
  f.advance(30 * 86400000);
  assert.equal((await f.request('DELETE','/auth/session',undefined,next.sessionToken)).status,401);
});

test('HTTP OTP wrong attempts commit, expiry and resend limits persist', async t => {
  const f = await fixture(); t.after(f.close);
  const a = await f.request('POST','/auth/challenges',{email:'a@example.com'});
  assert.equal((await f.request('POST','/auth/challenges',{email:'a@example.com'})).status,429);
  const actual = f.codes.get('a@example.com');
  const wrong = actual === '000000' ? '000001' : '000000';
  for (let i=0;i<5;i++) assert.equal((await f.request('POST','/auth/sessions',{challengeId:a.body.challengeId,code:wrong})).status,401);
  assert.equal((await f.request('POST','/auth/sessions',{challengeId:a.body.challengeId,code:actual})).status,401);
  const row = await f.db.challenge.findUniqueOrThrow({where:{id:a.body.challengeId}});
  assert.equal(row.attempts,5);
  f.advance(61000);
  const b = await f.request('POST','/auth/challenges',{email:'a@example.com'});
  const bcode = f.codes.get('a@example.com');
  f.advance(300000);
  assert.equal((await f.request('POST','/auth/sessions',{challengeId:b.body.challengeId,code:bcode})).status,401);
  for (let i=0;i<3;i++) { assert.equal((await f.request('POST','/auth/challenges',{email:'a@example.com'})).status,202); f.advance(61000); }
  assert.equal((await f.request('POST','/auth/challenges',{email:'a@example.com'})).status,429);
});

test('HTTP resend invalidates prior challenge; mail fails closed and invalid input is safe', async t => {
  const f = await fixture(); t.after(f.close);
  const first = await f.request('POST','/auth/challenges',{email:'a@example.com'});
  const old = f.codes.get('a@example.com'); f.advance(61000);
  await f.request('POST','/auth/challenges',{email:'a@example.com'});
  assert.equal((await f.request('POST','/auth/sessions',{challengeId:first.body.challengeId,code:old})).status,401);
  assert.equal((await f.request('POST','/auth/challenges',{email:'bad'})).status,422);
  assert.equal((await f.request('POST','/auth/sessions',{challengeId:randomUUID(),code:'123456'})).status,401);
  const closed = await fixture(smtpMailer({})); t.after(closed.close);
  const failure = await closed.request('POST','/auth/challenges',{email:'a@example.com'});
  assert.equal(failure.status,503);
  assert.equal(await closed.db.challenge.count(),0);
  assert.deepEqual(failure.body,{error:{code:'MAIL_UNAVAILABLE',message:'Email delivery unavailable'}});
});

test('HTTP IP send and verification quotas bound random-email and random-challenge abuse', async t => {
  const f = await fixture(); t.after(f.close);
  for (let i=0;i<20;i++) assert.equal((await f.request('POST','/auth/challenges',{email:`user${i}@example.com`})).status,202);
  assert.equal((await f.request('POST','/auth/challenges',{email:'other@example.com'})).status,429);
  for (let i=0;i<60;i++) assert.equal((await f.request('POST','/auth/sessions',{challengeId:randomUUID(),code:'123456'})).status,401);
  assert.equal((await f.request('POST','/auth/sessions',{challengeId:randomUUID(),code:'123456'})).status,429);
});
