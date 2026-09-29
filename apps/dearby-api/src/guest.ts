import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import type { DB } from './database.js';
import { readCard } from './cards.js';
import { ApiError, id } from './validation.js';

const digest = (token: string) => createHash('sha256').update(token).digest('hex');
const invalid = () => new ApiError(401, 'GUEST_SESSION_INVALID', 'Guest session unavailable');
const capacity = () => new ApiError(409, 'GUEST_CAPACITY_EXCEEDED', 'Guest storage limit reached');

export function guestRoutes(app: FastifyInstance, db: DB, proxySecret: string | undefined, now: () => number) {
  // Bounded, single-process abuse limits. No visitor IP is trusted or persisted.
  let windowStart = now();
  let requests = 0;
  let creations = 0;
  const sessionRequests = new Map<string, number>();
  function limited() { throw new ApiError(429, 'RATE_LIMITED', 'Try again later'); }
  function session(request: FastifyRequest, optional = false): string | undefined {
    const token = request.headers['x-guest-token'];
    if (token === undefined && optional) return undefined;
    if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(token)) throw invalid();
    const key = digest(token);
    if (!db.prepare('SELECT 1 FROM guest_sessions WHERE digest = ?').get(key)) throw invalid();
    const count = (sessionRequests.get(key) ?? 0) + 1;
    if (count > 120) limited();
    sessionRequests.set(key, count);
    return key;
  }
  void app.register(async guest => {
    guest.addHook('preHandler', async request => {
      if (!proxySecret || proxySecret.length < 32) throw new ApiError(503, 'GUEST_UNAVAILABLE', 'Guest storage unavailable');
      const supplied = request.headers['x-guest-proxy-key'];
      if (typeof supplied !== 'string' || !timingSafeEqual(Buffer.from(digest(supplied)), Buffer.from(digest(proxySecret)))) {
        throw new ApiError(403, 'FORBIDDEN', 'Server proxy required');
      }
      const time = now();
      if (time >= windowStart + 60000 || time < windowStart) {
        windowStart = time; requests = 0; creations = 0; sessionRequests.clear();
      }
      if (++requests > 1200) limited();
    });
    guest.get('/cards', async request => {
      const key = session(request)!;
      const rows = db.prepare(`SELECT g.card_id FROM guest_cards g JOIN cards c ON c.id = g.card_id
        WHERE g.session_digest = ? AND c.revoked = 0 ORDER BY g.rowid`).all(key) as {card_id: string}[];
      return {items: rows.map(row => readCard(db, row.card_id))};
    });
    guest.put('/cards/:id', async (request, reply) => {
      const key = session(request, true);
      const cardId = id.parse((request.params as {id: string}).id).toLowerCase();
      const result = db.transaction(() => {
        readCard(db, cardId); // The path identifies public content, never a visitor wallet.
        let sessionDigest = key;
        let guestToken: string | undefined;
        if (!sessionDigest) {
          if ((db.prepare('SELECT count(*) AS n FROM guest_sessions').get() as {n: number}).n >= 10000) throw capacity();
          if (creations >= 60) limited();
          guestToken = randomBytes(32).toString('base64url');
          sessionDigest = digest(guestToken);
          db.prepare('INSERT INTO guest_sessions VALUES (?)').run(sessionDigest);
        }
        // Withdrawn cards do not consume the active-card capacity.
        db.prepare('DELETE FROM guest_cards WHERE session_digest = ? AND card_id IN (SELECT id FROM cards WHERE revoked != 0)').run(sessionDigest);
        if (db.prepare('SELECT 1 FROM guest_cards WHERE session_digest = ? AND card_id = ?').get(sessionDigest, cardId)) {
          return {cardId, status: 'alreadySaved' as const};
        }
        if ((db.prepare('SELECT count(*) AS n FROM guest_cards WHERE session_digest = ?').get(sessionDigest) as {n: number}).n >= 100) throw capacity();
        db.prepare('INSERT INTO guest_cards VALUES (?, ?)').run(sessionDigest, cardId);
        return {cardId, status: 'saved' as const, ...(guestToken ? {guestToken} : {})};
      }).immediate();
      if ('guestToken' in result) { creations++; reply.code(201); }
      return result;
    });
    guest.delete('/cards/:id', async (request, reply) => {
      const key = session(request)!;
      const cardId = id.parse((request.params as {id: string}).id).toLowerCase();
      // Removing a withdrawn/already-removed reference is intentionally idempotent.
      db.prepare('DELETE FROM guest_cards WHERE session_digest = ? AND card_id = ?').run(key, cardId);
      return reply.code(204).send();
    });
    guest.delete('/session', async (request, reply) => {
      const key = session(request)!;
      db.prepare('DELETE FROM guest_sessions WHERE digest = ?').run(key);
      sessionRequests.delete(key);
      return reply.code(204).send();
    });
  }, {prefix: '/v1/guest'});
}
