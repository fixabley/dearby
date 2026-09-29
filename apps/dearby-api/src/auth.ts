import { createHash, createHmac, randomBytes, randomInt, randomUUID, timingSafeEqual } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { write, type DB } from './postgres.js';
import type { SendCode } from './mail.js';
import { ApiError, id } from './validation.js';
export type AuthOptions = { otpSecret: string; sendCode: SendCode; now?: () => number };
const tokenDigest = (value: string) => createHash('sha256').update(value).digest('hex');
export function authRoutes(app: FastifyInstance, db: DB, options: AuthOptions) {
  if (options.otpSecret.length < 32) throw new Error('OTP_SECRET must contain at least 32 characters');
  const now = options.now ?? Date.now;
  const digest = (value: string) => createHmac('sha256', options.otpSecret).update(value).digest('hex');
  async function limit(key: string, maximum: number, interval: number) {
    await write(db, async tx => {
      const row = await tx.rateLimit.findUnique({where:{key}});
      const time = BigInt(now());
      if (!row || row.windowStart + BigInt(interval) <= time) {
        await tx.rateLimit.upsert({where:{key},create:{key,windowStart:time,count:1},update:{windowStart:time,count:1}});
      } else {
        if (row.count >= maximum) throw new ApiError(429, 'RATE_LIMITED', 'Try again later');
        await tx.rateLimit.update({where:{key},data:{count:{increment:1}}});
      }
    });
  }
  const owner = async (request: FastifyRequest) => {
    const token = request.headers.authorization?.match(/^Bearer ([A-Za-z0-9_-]{43})$/)?.[1];
    const row = token ? await db.session.findUnique({where:{digest:tokenDigest(token)}}) : null;
    if (!row || row.expiresAt <= BigInt(now())) throw new ApiError(401, 'UNAUTHORIZED', 'Sign in required');
    return row.profileId;
  };
  app.post('/v1/auth/challenges', async (request, reply) => {
    const {email} = z.strictObject({email: z.email().max(254).transform(e => e.trim().toLowerCase())}).parse(request.body);
    await limit(`challenge-ip:${digest(request.ip)}`, 20, 3600000);
    await limit(`challenge-email:${digest(email)}`, 1, 60000);
    await limit(`challenge-hour:${digest(email)}`, 5, 3600000);
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
  app.post('/v1/auth/sessions', async (request) => {
    const input = z.strictObject({challengeId: id, code: z.string().regex(/^\d{6}$/)}).parse(request.body);
    await limit(`verify-ip:${digest(request.ip)}`, 60, 60000);
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
  app.delete('/v1/auth/session', async (request, reply) => {
    await owner(request);
    await write(db, tx => tx.session.deleteMany({where:{digest:tokenDigest(request.headers.authorization!.slice(7))}}));
    return reply.code(204).send();
  });
  return owner;
}
