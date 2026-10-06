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

test('HTTP per-address send limits: resend interval, hourly and daily caps; other addresses unaffected', async t => {
  const f = await fixture(); t.after(f.close);
  const send = (email: string, forwarded?: string) => f.app.inject({method:'POST',url:'/v1/auth/challenges',payload:{email},
    headers:forwarded ? {'x-forwarded-for':forwarded} : {}});
  assert.equal((await send('A@Example.com')).statusCode,202);
  // The address is normalized before its digest keys the limit; forwarded headers cannot pose as another client.
  for (const [email,forwarded] of [['a@example.com',undefined],['A@EXAMPLE.COM','198.51.100.7']] as const)
    assert.equal((await send(email,forwarded)).statusCode,429);
  assert.equal((await send('b@example.com')).statusCode,202);
  f.advance(59999); assert.equal((await send('a@example.com')).statusCode,429);
  f.advance(1); assert.equal((await send('a@example.com')).statusCode,202);
  for (let i=0;i<3;i++) { f.advance(60000); assert.equal((await send('a@example.com')).statusCode,202); }
  f.advance(60000); assert.equal((await send('a@example.com')).statusCode,429); // 5 per hour.
  for (let i=0;i<5;i++) { f.advance(3600000); assert.equal((await send('a@example.com')).statusCode,202); }
  f.advance(3600000); assert.equal((await send('a@example.com')).statusCode,429); // 10 per day.
  f.advance(86400000); assert.equal((await send('a@example.com')).statusCode,202);
});

test('HTTP service-wide send caps stay below the mail account quota; rejected requests consume nothing', async t => {
  const f = await fixture(); t.after(f.close);
  const send = (email: string) => f.request('POST','/auth/challenges',{email});
  for (let hourIndex=0;hourIndex<4;hourIndex++) {
    for (let i=0;i<100;i++) assert.equal((await send(`h${hourIndex}-${i}@example.com`)).status,202);
    const capped = await send(`late${hourIndex}@example.com`);
    assert.equal(capped.status,429); assert.deepEqual(capped.body,{error:{code:'RATE_LIMITED',message:'Try again later'}});
    f.advance(3600000);
  }
  assert.equal((await send('next-hour@example.com')).status,429); // 400 per day.
  f.advance(86400000);
  // A request rejected by the service cap did not start its own address interval.
  assert.equal((await send('late0@example.com')).status,202);
  assert.equal(await f.db.challenge.count(),401);
});

test('HTTP same challenge response for new and existing accounts; verification bounded per code and service-wide', async t => {
  const f = await fixture(); t.after(f.close);
  await f.login('existing@example.com');
  f.advance(60000);
  const existing = await f.request('POST','/auth/challenges',{email:'existing@example.com'});
  const fresh = await f.request('POST','/auth/challenges',{email:'new@example.com'});
  assert.equal(existing.status,202); assert.equal(fresh.status,202);
  assert.deepEqual(Object.keys(existing.body).sort(),Object.keys(fresh.body).sort());
  assert.equal(Date.parse(existing.body.expiresAt),Date.parse(fresh.body.expiresAt));
  const [again, againNew] = [await f.request('POST','/auth/challenges',{email:'existing@example.com'}),await f.request('POST','/auth/challenges',{email:'new@example.com'})];
  assert.deepEqual([again.status,again.body],[againNew.status,againNew.body]);
  for (let i=0;i<600;i++) assert.equal((await f.request('POST','/auth/sessions',{challengeId:randomUUID(),code:'123456'})).status,401);
  assert.equal((await f.request('POST','/auth/sessions',{challengeId:fresh.body.challengeId,code:f.codes.get('new@example.com')})).status,429);
  f.advance(60000);
  assert.equal((await f.request('POST','/auth/sessions',{challengeId:fresh.body.challengeId,code:f.codes.get('new@example.com')})).status,200);
});
