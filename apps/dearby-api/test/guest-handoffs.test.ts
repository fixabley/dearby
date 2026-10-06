import { test } from 'node:test';
import assert from 'node:assert/strict';
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
