import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { fixture } from './helpers.js';

test('handoff rows allow one per session and are removed with their guest session', async t => {
  const f = await fixture(); t.after(f.close);
  const session = 'a'.repeat(64);
  await f.db.guestSession.create({data:{digest:session}});
  await f.db.guestHandoff.create({data:{digest:'b'.repeat(64),sessionDigest:session,tokenCiphertext:'x',expiresAt:1n}});
  await assert.rejects(f.db.guestHandoff.create({data:{digest:'c'.repeat(64),sessionDigest:session,expiresAt:1n}}));
  await f.db.guestSession.delete({where:{digest:session}});
  assert.equal(await f.db.guestHandoff.count(),0);
});

const secret = 'test-only-guest-proxy-secret-not-for-runtime';
const hash = (value: string) => createHash('sha256').update(value).digest('hex');
async function setup() {
  const f = await fixture(undefined, secret);
  const alice = await f.login('alice@example.com');
  const card = (await f.request('POST','/cards',{name:'Saved',description:'',contactIds:[],historyIds:[]},alice.sessionToken)).body;
  const call = (method: 'GET'|'PUT'|'POST'|'DELETE', path: string, token?: string, body?: unknown, proxy: string | null = secret) => f.app.inject({method,url:`/v1/guest${path}`,
    headers:{...(proxy === null ? {} : {'x-guest-proxy-key':proxy}),...(token === undefined ? {} : {'x-guest-token':token}),...(body === undefined ? {} : {'content-type':'application/json'})},
    ...(body === undefined ? {} : {payload:JSON.stringify(body)})});
  const token = (await call('PUT',`/cards/${card.id}`)).json().guestToken as string;
  const issue = async () => (await call('POST','/handoffs',token)).json() as {code: string; expiresAt: string};
  const redeem = (code: unknown) => call('POST','/handoffs/redeem',undefined,{code});
  return {...f, card, call, token, issue, redeem};
}
const notFound = {error:{code:'NOT_FOUND',message:'Resource not found'}};

test('handoff code returns the same token once; storage keeps only digests and a code-keyed ciphertext', async () => {
  const f = await setup();
  try {
    assert.equal((await f.call('POST','/handoffs')).statusCode,401);
    assert.equal((await f.call('POST','/handoffs',f.token,undefined,null)).statusCode,403);
    assert.equal((await f.call('POST','/handoffs/redeem',undefined,{code:'x'},null)).statusCode,403);
    const issued = await f.call('POST','/handoffs',f.token);
    assert.equal(issued.statusCode,201);assert.equal(issued.headers['cache-control'],'no-store');
    const {code, expiresAt} = issued.json();
    assert.match(code,/^[A-Za-z0-9_-]{43}$/);
    const row = await f.db.guestHandoff.findUniqueOrThrow({where:{sessionDigest:hash(f.token)}});
    assert.equal(row.digest,hash(code));
    assert.equal(Number(row.expiresAt),Date.parse(expiresAt));
    const stored = JSON.stringify(row,(_,v)=>typeof v==='bigint'?String(v):v);
    assert.ok(!stored.includes(code));assert.ok(!stored.includes(f.token));
    for (const body of [{},{code:1},{code:''},{code:'x',extra:true}]) assert.equal((await f.call('POST','/handoffs/redeem',undefined,body)).statusCode,422);
    const redeemed = await f.redeem(code);
    assert.equal(redeemed.statusCode,200);
    assert.deepEqual(redeemed.json(),{guestToken:f.token});
    assert.deepEqual((await f.call('GET','/cards',redeemed.json().guestToken)).json().items,[f.card]);
    const used = await f.db.guestHandoff.findUniqueOrThrow({where:{digest:hash(code)}});
    assert.equal(used.tokenCiphertext,null);assert.notEqual(used.usedAt,null);
    const reuse = await f.redeem(code);
    assert.equal(reuse.statusCode,404);assert.deepEqual(reuse.json(),notFound);
    assert.deepEqual((await f.redeem('A'.repeat(43))).json(),notFound);
    assert.equal(await f.db.guestSession.count(),1); // Redeeming never creates a session.
  } finally {await f.close();}
});

test('handoff codes expire after 10 minutes, are superseded by a new issue and die with the session', async () => {
  const f = await setup();
  try {
    const early = await f.issue();
    f.advance(10 * 60 * 1000 - 1);
    assert.equal((await f.redeem(early.code)).statusCode,200);
    const late = await f.issue();
    f.advance(10 * 60 * 1000);
    const expired = await f.redeem(late.code);
    assert.equal(expired.statusCode,404);assert.deepEqual(expired.json(),notFound);
    assert.equal((await f.db.guestHandoff.findUniqueOrThrow({where:{digest:hash(late.code)}})).tokenCiphertext,null);
    // An expired ciphertext is also cleared by any later handoff request, not only by redeeming that code.
    const idle = await f.issue();
    const other = (await f.call('PUT',`/cards/${f.card.id}`)).json().guestToken;
    f.advance(10 * 60 * 1000);
    await f.call('POST','/handoffs',other);
    assert.equal((await f.db.guestHandoff.findUniqueOrThrow({where:{digest:hash(idle.code)}})).tokenCiphertext,null);
    const first = await f.issue();
    const second = await f.issue();
    assert.notEqual(first.code,second.code);
    assert.deepEqual((await f.redeem(first.code)).json(),notFound);
    assert.equal(await f.db.guestHandoff.count({where:{sessionDigest:hash(f.token)}}),1);
    const orphan = await f.issue();
    assert.equal((await f.call('DELETE','/session',f.token)).statusCode,204);
    assert.deepEqual((await f.redeem(orphan.code)).json(),notFound);
    assert.equal(await f.db.guestHandoff.count({where:{sessionDigest:hash(f.token)}}),0);
    assert.equal((await f.call('POST','/handoffs',f.token)).statusCode,401);
  } finally {await f.close();}
});

test('redeem failures count towards the shared guest request limit', async () => {
  const f = await setup();
  try {
    const {code} = await f.issue();
    f.advance(60000);
    for (let i=0;i<1200;i++) assert.equal((await f.redeem(randomBytes(32).toString('base64url'))).statusCode,404);
    assert.equal((await f.redeem(code)).statusCode,429);
    assert.equal((await f.call('GET','/cards',f.token)).statusCode,429);
    f.advance(60000);
    assert.equal((await f.redeem(code)).statusCode,200);
  } finally {await f.close();}
});
