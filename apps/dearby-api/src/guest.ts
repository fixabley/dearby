import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { write, type DB } from './postgres.js';
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
  async function session(request: FastifyRequest, optional = false): Promise<string | undefined> {
    const token = request.headers['x-guest-token'];
    if (token === undefined && optional) return undefined;
    if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(token)) throw invalid();
    const key = digest(token);
    if (!await db.guestSession.findUnique({where:{digest:key}})) throw invalid();
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
      const key = (await session(request))!;
      const rows = await db.guestCard.findMany({where:{sessionDigest:key,card:{revoked:false}},include:{card:true},orderBy:{ordinal:'asc'}});
      return {items:rows.map(row => JSON.parse(row.card.data))};
    });
    guest.put('/cards/:id', async (request, reply) => {
      const key = await session(request, true);
      const cardId = id.parse((request.params as {id: string}).id).toLowerCase();
      const result = await write(db, async tx => {
        await readCard(tx, cardId); // The path identifies public content, never a visitor wallet.
        let sessionDigest = key;
        let guestToken: string | undefined;
        if (!sessionDigest) {
          if (await tx.guestSession.count() >= 10000) throw capacity();
          if (creations >= 60) limited();
          guestToken = randomBytes(32).toString('base64url');
          sessionDigest = digest(guestToken);
          await tx.guestSession.create({data:{digest:sessionDigest}});
        }
        if (key && !await tx.guestSession.findUnique({where:{digest:key}})) throw invalid();
        // Withdrawn cards do not consume the active-card capacity.
        await tx.guestCard.deleteMany({where:{sessionDigest,card:{revoked:true}}});
        if (await tx.guestCard.findUnique({where:{sessionDigest_cardId:{sessionDigest,cardId}}})) {
          return {cardId, status: 'alreadySaved' as const};
        }
        if (await tx.guestCard.count({where:{sessionDigest}}) >= 100) throw capacity();
        await tx.guestCard.create({data:{sessionDigest,cardId}});
        return {cardId, status: 'saved' as const, ...(guestToken ? {guestToken} : {})};
      });
      if ('guestToken' in result) { creations++; reply.code(201); }
      return result;
    });
    guest.delete('/cards/:id', async (request, reply) => {
      const key = (await session(request))!;
      const cardId = id.parse((request.params as {id: string}).id).toLowerCase();
      // Removing a withdrawn/already-removed reference is intentionally idempotent.
      await write(db, tx => tx.guestCard.deleteMany({where:{sessionDigest:key,cardId}}));
      return reply.code(204).send();
    });
    guest.delete('/session', async (request, reply) => {
      const key = (await session(request))!;
      await write(db, tx => tx.guestSession.deleteMany({where:{digest:key}}));
      sessionRequests.delete(key);
      return reply.code(204).send();
    });
  }, {prefix: '/v1/guest'});
}
