import { createHash, createHmac, randomBytes, randomInt, randomUUID, timingSafeEqual } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { z } from 'zod';
import type { DB } from './database.js';
import type { SendCode } from './mail.js';
import { ApiError, id } from './validation.js';
export type AuthOptions = { otpSecret: string; sendCode: SendCode; now?: () => number };
const tokenDigest = (value: string) => createHash('sha256').update(value).digest('hex');
export function authRoutes(app: FastifyInstance, db: DB, options: AuthOptions) {
  if (options.otpSecret.length < 32) throw new Error('OTP_SECRET must contain at least 32 characters');
  const now = options.now ?? Date.now;
  const digest = (value: string) => createHmac('sha256', options.otpSecret).update(value).digest('hex');
  function limit(key: string, maximum: number, interval: number) {
    db.transaction(() => {
      const row = db.prepare('SELECT * FROM rate_limits WHERE key = ?').get(key) as {window_start: number; count: number} | undefined;
      if (!row || row.window_start + interval <= now()) {
        db.prepare('INSERT OR REPLACE INTO rate_limits VALUES (?, ?, 1)').run(key, now());
      } else {
        if (row.count >= maximum) throw new ApiError(429, 'RATE_LIMITED', 'Try again later');
        db.prepare('UPDATE rate_limits SET count = count + 1 WHERE key = ?').run(key);
      }
    }).immediate();
  }
  const owner = (request: FastifyRequest) => {
    const token = request.headers.authorization?.match(/^Bearer ([A-Za-z0-9_-]{43})$/)?.[1];
    const row = token && db.prepare('SELECT profile_id FROM sessions WHERE digest = ? AND expires_at > ?').get(tokenDigest(token), now()) as {profile_id: string} | undefined;
    if (!row) throw new ApiError(401, 'UNAUTHORIZED', 'Sign in required');
    return row.profile_id;
  };
  app.post('/v1/auth/challenges', async (request, reply) => {
    const {email} = z.strictObject({email: z.email().max(254).transform(e => e.trim().toLowerCase())}).parse(request.body);
    limit(`challenge-ip:${digest(request.ip)}`, 20, 3600000);
    limit(`challenge-email:${digest(email)}`, 1, 60000);
    limit(`challenge-hour:${digest(email)}`, 5, 3600000);
    const challengeId = randomUUID();
    const code = String(randomInt(1000000)).padStart(6, '0');
    const expires = now() + 300000;
    db.prepare('INSERT INTO challenges (id,email,digest,expires_at) VALUES (?,?,?,?)').run(challengeId, email, digest(`${challengeId}:${code}`), expires);
    try { await options.sendCode(email, code); }
    catch {
      db.prepare('DELETE FROM challenges WHERE id = ?').run(challengeId);
      throw new ApiError(503, 'MAIL_UNAVAILABLE', 'Email delivery unavailable');
    }
    // Only successfully sent newer challenges invalidate earlier codes.
    db.prepare('UPDATE challenges SET consumed = 1 WHERE email = ? AND id != ?').run(email, challengeId);
    return reply.code(202).send({challengeId, expiresAt: new Date(expires).toISOString()});
  });
  app.post('/v1/auth/sessions', async (request) => {
    const input = z.strictObject({challengeId: id, code: z.string().regex(/^\d{6}$/)}).parse(request.body);
    limit(`verify-ip:${digest(request.ip)}`, 60, 60000);
    const result = db.transaction(() => {
      const row = db.prepare('SELECT * FROM challenges WHERE id = ?').get(input.challengeId) as {email:string; digest:string; expires_at:number; attempts:number; consumed:number} | undefined;
      if (!row || row.consumed || row.expires_at <= now() || row.attempts >= 5) return null;
      db.prepare('UPDATE challenges SET attempts = attempts + 1 WHERE id = ?').run(input.challengeId);
      if (!timingSafeEqual(Buffer.from(row.digest, 'hex'), Buffer.from(digest(`${input.challengeId}:${input.code}`), 'hex'))) return null;
      db.prepare('UPDATE challenges SET consumed = 1 WHERE id = ?').run(input.challengeId);
      let profile = db.prepare('SELECT id FROM profiles WHERE email = ?').get(row.email) as {id: string} | undefined;
      if (!profile) {
        profile = {id: randomUUID()};
        db.prepare('INSERT INTO profiles VALUES (?, ?, ?)').run(profile.id, row.email, JSON.stringify({id: profile.id, name:'',job:'',introduction:'',contacts:[],histories:[],updatedAt:new Date(now()).toISOString()}));
      }
      const sessionToken = randomBytes(32).toString('base64url');
      db.prepare('INSERT INTO sessions VALUES (?,?,?)').run(tokenDigest(sessionToken), profile.id, now() + 30 * 86400000);
      return {sessionToken, profileId: profile.id};
    }).immediate();
    if (!result) throw new ApiError(401, 'INVALID_CODE', 'Invalid or expired code');
    return result;
  });
  app.delete('/v1/auth/session', async (request, reply) => {
    owner(request);
    db.prepare('DELETE FROM sessions WHERE digest = ?').run(tokenDigest(request.headers.authorization!.slice(7)));
    return reply.code(204).send();
  });
  return owner;
}
