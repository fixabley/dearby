import {documented,guestOnly,guestSecurity,guestFirstSaveSecurity,guestProxySecurity,idParams,handoffSchema,handoffRedeemedSchema,guestListSchema,guestSavedSchema,guestCreatedSchema,guestShareSavedSchema,guestShareCreatedSchema} from './openapi.js';
import { createCipheriv, createDecipheriv, createHash, hkdfSync, randomBytes, timingSafeEqual } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { write, type DB, type Transaction } from './postgres.js';
import { readCard } from './cards.js';
import { readShare } from './shares.js';
import { ApiError, handoffInput, id, missing } from './validation.js';

const digest = (token: string) => createHash('sha256').update(token).digest('hex');
const invalid = () => new ApiError(401, 'GUEST_SESSION_INVALID', 'Guest session unavailable');
const capacity = () => new ApiError(409, 'GUEST_CAPACITY_EXCEEDED', 'Guest storage limit reached');
const handoffLifetime = 10 * 60 * 1000;
// The 256-bit code is the only key material: the database holds its digest, never the code, so it cannot decrypt the token.
// The session digest is authenticated data, binding a ciphertext to its own row.
const handoffKey = (code: string) => Buffer.from(hkdfSync('sha256', code, 'dearby/guest-handoff', 'v1 token key', 32));
function seal(token: string, code: string, sessionDigest: string) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', handoffKey(code), iv).setAAD(Buffer.from(sessionDigest));
  const body = Buffer.concat([cipher.update(token, 'utf8'), cipher.final()]);
  return [iv, body, cipher.getAuthTag()].map(part => part.toString('base64url')).join('.');
}
function unseal(sealed: string, code: string, sessionDigest: string) {
  const [iv, body, tag] = sealed.split('.').map(part => Buffer.from(part, 'base64url'));
  const decipher = createDecipheriv('aes-256-gcm', handoffKey(code), iv).setAAD(Buffer.from(sessionDigest)).setAuthTag(tag);
  return Buffer.concat([decipher.update(body), decipher.final()]).toString('utf8');
}
// Expired ciphertexts are cleared by the next handoff request; a row without the code is already undecryptable.
const clearExpired = (tx: Transaction, time: number) =>
  tx.guestHandoff.updateMany({where:{expiresAt:{lte:BigInt(time)},tokenCiphertext:{not:null}},data:{tokenCiphertext:null}});

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
  // Shared by card and share saves inside the caller's transaction; a tokenless save creates the session.
  async function saveCard(tx: Transaction, key: string | undefined, cardId: string) {
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
    // Withdrawn cards do not consume the active-card capacity; their share links cascade.
    await tx.guestCard.deleteMany({where:{sessionDigest,card:{revoked:true}}});
    if (await tx.guestCard.findUnique({where:{sessionDigest_cardId:{sessionDigest,cardId}}})) return {sessionDigest, guestToken, cardSaved: false};
    if (await tx.guestCard.count({where:{sessionDigest}}) >= 100) throw capacity();
    await tx.guestCard.create({data:{sessionDigest,cardId}});
    return {sessionDigest, guestToken, cardSaved: true};
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
    guest.get('/cards', documented({operationId:'getGuestWallet',summary:'Read one guest wallet via the trusted server proxy',tag:'Guest proxy',description:guestOnly+' No token returns 401. Next returns an empty list without calling this route when its cookie is absent. shares lists share links in save order with their activity snapshots. Withdrawn cards and their shares are omitted; private profiles and other wallets cannot be accessed. Request limits 120/token/minute and 1200/proxy/minute are process-local.',security:guestSecurity,responses:{200:guestListSchema},errors:[401,403,429,503]}), async request => {
      const key = (await session(request))!;
      const [rows, links] = await Promise.all([
        db.guestCard.findMany({where:{sessionDigest:key,card:{revoked:false}},include:{card:true},orderBy:{ordinal:'asc'}}),
        db.guestCardShare.findMany({where:{sessionDigest:key,guestCard:{card:{revoked:false}}},include:{share:true},orderBy:{ordinal:'asc'}}),
      ]);
      return {items:rows.map(row => JSON.parse(row.card.data)),
        shares:links.map(link => ({cardId:link.cardId,shareId:link.shareId,activities:JSON.parse(link.share.activities),savedAt:link.savedAt}))};
    });
    guest.put('/cards/:id', documented({operationId:'saveGuestCard',summary:'Atomically save a public card; first save creates a guest session',tag:'Guest proxy',description:guestOnly+' No body required. X-Guest-Token is optional only on first save: 201 returns a new guestToken to the server proxy; existing token returns 200 saved/alreadySaved, without token rotation. Invalid/revoked token returns 401, never automatic replacement. UUID/database existence/withdrawal validated before storage. Max 100 active cards/session and 10000 total sessions; withdrawn cards free active capacity on save. New sessions throttled 60/minute/process independently of indefinite lifetime. Never publish or create a card here.',security:guestFirstSaveSecurity,params:idParams,responses:{200:guestSavedSchema,201:guestCreatedSchema},errors:[401,403,404,409,429,503]}), async (request, reply) => {
      const key = await session(request, true);
      const cardId = id.parse((request.params as {id: string}).id).toLowerCase();
      const result = await write(db, async tx => {
        await readCard(tx, cardId); // The path identifies public content, never a visitor wallet.
        const {guestToken, cardSaved} = await saveCard(tx, key, cardId);
        return {cardId, status: cardSaved ? 'saved' as const : 'alreadySaved' as const, ...(guestToken ? {guestToken} : {})};
      });
      if ('guestToken' in result) { creations++; reply.code(201); }
      return result;
    });
    guest.put('/shares/:id', documented({operationId:'saveGuestShare',summary:'Save a shared card and link the share to it; first save creates a guest session',tag:'Guest proxy',description:guestOnly+' No body required. Token, 201/200, session and card-capacity rules match saveGuestCard. The card is stored once per session; each new share of it adds a link (saved), an already-linked share returns alreadySaved. Max 20 share links per saved card; re-saving a linked share succeeds at the limit. Missing shares and shares of withdrawn cards return 404.',security:guestFirstSaveSecurity,params:idParams,responses:{200:guestShareSavedSchema,201:guestShareCreatedSchema},errors:[401,403,404,409,429,503]}), async (request, reply) => {
      const key = await session(request, true);
      const shareId = id.parse((request.params as {id: string}).id).toLowerCase();
      const result = await write(db, async tx => {
        const {cardId} = (await readShare(tx, shareId)).share;
        const {sessionDigest, guestToken} = await saveCard(tx, key, cardId);
        // Links cascade with their saved card, so an existing link implies the card was already saved.
        if (await tx.guestCardShare.findUnique({where:{sessionDigest_shareId:{sessionDigest,shareId}}})) {
          return {cardId, shareId, status: 'alreadySaved' as const};
        }
        if (await tx.guestCardShare.count({where:{sessionDigest,cardId}}) >= 20) throw capacity();
        await tx.guestCardShare.create({data:{sessionDigest,cardId,shareId,savedAt:new Date(now()).toISOString()}});
        return {cardId, shareId, status: 'saved' as const, ...(guestToken ? {guestToken} : {})};
      });
      if ('guestToken' in result) { creations++; reply.code(201); }
      return result;
    });
    guest.delete('/cards/:id', documented({operationId:'removeGuestCard',summary:'Remove one saved reference from your guest wallet',tag:'Guest proxy',description:guestOnly+' Also removes that card\'s share links. Removal is idempotent, including already-removed or withdrawn card references; valid UUID required. Does not delete the public card.',security:guestSecurity,params:idParams,responses:{204:null},errors:[401,403,429,503]}), async (request, reply) => {
      const key = (await session(request))!;
      const cardId = id.parse((request.params as {id: string}).id).toLowerCase();
      // Removing a withdrawn/already-removed reference is intentionally idempotent.
      await write(db, tx => tx.guestCard.deleteMany({where:{sessionDigest:key,cardId}}));
      return reply.code(204).send();
    });
    guest.delete('/session', documented({operationId:'deleteGuestSession',summary:'Explicitly discard a guest session and its saved references',tag:'Guest proxy',description:guestOnly+' Cascades its saved references and share links. Reuse returns 401; Next also deletes its cookie on already-revoked 401. No profile, other wallet or public card is deleted.',security:guestSecurity,responses:{204:null},errors:[401,403,429,503]}), async (request, reply) => {
      const key = (await session(request))!;
      await write(db, tx => tx.guestSession.deleteMany({where:{digest:key}}));
      sessionRequests.delete(key);
      return reply.code(204).send();
    });
    guest.post('/handoffs', documented({operationId:'createGuestHandoff',summary:'Issue a one-time code that hands this guest session to the home-screen web app',tag:'Guest proxy',description:guestOnly+' Existing token required. 201 returns a 256-bit base64url code valid for 10 minutes, single use. One live code per session: issuing again invalidates the previous code. Stores only the code digest, expiry, use time and the token encrypted with a key derived from the code. Next puts the code only into the no-store manifest start_url; never log it.',security:guestSecurity,responses:{201:handoffSchema},errors:[401,403,429,503]}), async (request, reply) => {
      const key = (await session(request))!;
      const code = randomBytes(32).toString('base64url');
      const time = now();
      const data = {digest:digest(code),tokenCiphertext:seal(request.headers['x-guest-token'] as string,code,key),expiresAt:BigInt(time + handoffLifetime),usedAt:null};
      await write(db, async tx => {
        await clearExpired(tx, time);
        if (!await tx.guestSession.findUnique({where:{digest:key}})) throw invalid();
        await tx.guestHandoff.upsert({where:{sessionDigest:key},create:{...data,sessionDigest:key},update:data});
      });
      return reply.code(201).send({code,expiresAt:new Date(time + handoffLifetime).toISOString()});
    });
    guest.post('/handoffs/redeem', documented({operationId:'redeemGuestHandoff',summary:'Exchange a one-time handoff code for the same guest token',tag:'Guest proxy',description:guestOnly+' No guest token. Returns the exact token of the issuing session so Next can set the same cookie. Unknown, expired, used, superseded and discarded-session codes all return the same 404. Success marks the code used and erases its ciphertext. Every attempt, including failures, counts towards the 1200/proxy/minute guest limit.',security:guestProxySecurity,body:handoffInput,bodyExample:{code:'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'},responses:{200:handoffRedeemedSchema},errors:[403,404,429,503]}), async request => {
      const {code} = handoffInput.parse(request.body);
      const time = now();
      // Failures return null instead of throwing so the expired-ciphertext cleanup still commits.
      const guestToken = await write(db, async tx => {
        await clearExpired(tx, time);
        const row = await tx.guestHandoff.findUnique({where:{digest:digest(code)}});
        if (!row?.tokenCiphertext || row.usedAt !== null || BigInt(time) >= row.expiresAt) return null;
        let token: string;
        try { token = unseal(row.tokenCiphertext, code, row.sessionDigest); } catch { return null; }
        await tx.guestHandoff.update({where:{digest:row.digest},data:{usedAt:BigInt(time),tokenCiphertext:null}});
        return token;
      });
      if (guestToken === null) throw missing();
      return {guestToken};
    });
  }, {prefix: '/v1/guest'});
}
