import {documented,challengeResponse,sessionResponse,ownerSecurity,ownerOnly} from './openapi.js';
import { createHash, createHmac, randomBytes, randomInt, randomUUID, timingSafeEqual } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { write, type DB } from './postgres.js';
import type { SendCode } from './mail.js';
import { ApiError, challengeInput, sessionInput } from './validation.js';
export type AuthOptions = { otpSecret: string; sendCode: SendCode; now?: () => number };
const tokenDigest = (value: string) => createHash('sha256').update(value).digest('hex');
const minute = 60000, hour = 3600000, day = 86400000;
export function authRoutes(app: FastifyInstance, db: DB, options: AuthOptions) {
  if (options.otpSecret.length < 32) throw new Error('OTP_SECRET must contain at least 32 characters');
  const now = options.now ?? Date.now;
  const digest = (value: string) => createHmac('sha256', options.otpSecret).update(value).digest('hex');
  // All rules are checked before any counter moves, so a rejected request never consumes another rule's quota.
  async function limit(rules: [key: string, maximum: number, interval: number][]) {
    await write(db, async tx => {
      const time = BigInt(now());
      const rows = await Promise.all(rules.map(([key]) => tx.rateLimit.findUnique({where:{key}})));
      const fresh = rules.map(([,,interval],i) => !rows[i] || rows[i].windowStart + BigInt(interval) <= time);
      if (rules.some(([,maximum],i) => !fresh[i] && rows[i]!.count >= maximum)) throw new ApiError(429, 'RATE_LIMITED', 'Try again later');
      for (const [i,[key]] of rules.entries()) {
        if (fresh[i]) await tx.rateLimit.upsert({where:{key},create:{key,windowStart:time,count:1},update:{windowStart:time,count:1}});
        else await tx.rateLimit.update({where:{key},data:{count:{increment:1}}});
      }
    });
  }
  const owner = async (request: FastifyRequest) => {
    const token = request.headers.authorization?.match(/^Bearer ([A-Za-z0-9_-]{43})$/)?.[1];
    const row = token ? await db.session.findUnique({where:{digest:tokenDigest(token)}}) : null;
    if (!row || row.expiresAt <= BigInt(now())) throw new ApiError(401, 'UNAUTHORIZED', 'Sign in required');
    return row.profileId;
  };
  app.post('/v1/auth/challenges', documented({operationId:'createChallenge',summary:'Request an email verification code',tag:'Authentication',
    description:'Direct/local API only; current public ingress returns 404. Email normalized to lowercase. Code never appears in a response; expires after 5 minutes, at most 5 verification attempts. Limits per normalized email: 1/minute, 5/hour, 10/day; service-wide 100/hour and 400/day (below the ~500/day of a personal Gmail sender). Failed deliveries count. The same 202 shape is returned for new and existing accounts; only 429/503 differ. SMTP absent/failure returns 503, never a claimed successful delivery.',
    body:challengeInput,responses:{202:challengeResponse},errors:[429,503]}), async (request, reply) => {
    const {email} = challengeInput.parse(request.body);
    // Per-address and service-wide limits; the proxy hides client IPs, so no IP key is used (2026-10-06).
    const address = digest(email);
    await limit([[`challenge-email:${address}`,1,minute],[`challenge-hour:${address}`,5,hour],[`challenge-day:${address}`,10,day],
      ['challenge-all-hour',100,hour],['challenge-all-day',400,day]]);
    const challengeId = randomUUID();
    const code = String(randomInt(1000000)).padStart(6, '0');
    const expires = now() + 300000;
    await write(db, tx => tx.challenge.create({data:{id:challengeId,email,digest:digest(`${challengeId}:${code}`),expiresAt:BigInt(expires)}}));
    try { await options.sendCode(email, code); }
    catch {
      await write(db, tx => tx.challenge.delete({where:{id:challengeId}}));
      throw new ApiError(503, 'MAIL_UNAVAILABLE', 'Email delivery unavailable');
    }
    // Only successfully sent newer challenges invalidate earlier codes.
    await write(db, tx => tx.challenge.updateMany({where:{email,id:{not:challengeId}},data:{consumed:true}}));
    return reply.code(202).send({challengeId, expiresAt: new Date(expires).toISOString()});
  });
  app.post('/v1/auth/sessions', documented({operationId:'createOwnerSession',summary:'Exchange a one-use email code for an owner session',tag:'Authentication',
    description:'Direct/local API only; current public ingress returns 404. Returns an opaque 30-day bearer session and profile ID. Code can succeed only once. Wrong attempts persist; consumed/expired/unknown/too-many-attempts returns 401. Each code allows 5 attempts; service-wide 600 verifications/minute.',
    body:sessionInput,responses:{200:sessionResponse},errors:[401,429]}), async (request) => {
    const input = sessionInput.parse(request.body);
    await limit([['verify-all',600,minute]]); // Load bound; guessing is bounded by 5 attempts per code.
    const result = await write(db, async tx => {
      const row = await tx.challenge.findUnique({where:{id:input.challengeId}});
      if (!row || row.consumed || row.expiresAt <= BigInt(now()) || row.attempts >= 5) return null;
      await tx.challenge.update({where:{id:input.challengeId},data:{attempts:{increment:1}}});
      if (!timingSafeEqual(Buffer.from(row.digest,'hex'),Buffer.from(digest(`${input.challengeId}:${input.code}`),'hex'))) return null;
      await tx.challenge.update({where:{id:input.challengeId},data:{consumed:true}});
      let profile = await tx.profile.findUnique({where:{email:row.email}});
      if (!profile) {
        const id = randomUUID();
        profile = await tx.profile.create({data:{id,email:row.email,data:JSON.stringify({id,name:'',job:'',introduction:'',contacts:[],histories:[],updatedAt:new Date(now()).toISOString()})}});
      }
      const sessionToken = randomBytes(32).toString('base64url');
      await tx.session.create({data:{digest:tokenDigest(sessionToken),profileId:profile.id,expiresAt:BigInt(now() + 30 * 86400000)}});
      return {sessionToken,profileId:profile.id};
    });
    if (!result) throw new ApiError(401, 'INVALID_CODE', 'Invalid or expired code');
    return result;
  });
  app.delete('/v1/auth/session', documented({operationId:'deleteOwnerSession',summary:'Revoke the current owner session',tag:'Authentication',
    description:ownerOnly+' Revokes only the supplied bearer token. Reusing a revoked token returns 401.',security:ownerSecurity,responses:{204:null},errors:[401]}), async (request, reply) => {
    await owner(request);
    await write(db, tx => tx.session.deleteMany({where:{digest:tokenDigest(request.headers.authorization!.slice(7))}}));
    return reply.code(204).send();
  });
  return owner;
}
