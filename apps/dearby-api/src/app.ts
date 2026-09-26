import Fastify from 'fastify';
import { ZodError } from 'zod';
import type { DB } from './database.js';
import { ApiError } from './validation.js';
import { walletRoutes } from './wallet.js';
import { cardRoutes } from './cards.js';
import { authRoutes, type AuthOptions } from './auth.js';

export function createApp(db: DB, options: AuthOptions) {
  const app = Fastify({ logger: false, bodyLimit: 65536, trustProxy: false });
  app.addHook('onSend', async (_request, reply) => { reply.header('Cache-Control', 'no-store'); });
  app.setErrorHandler((error, _request, reply) => {
    const status = error instanceof ZodError ? 422 : error instanceof ApiError ? error.statusCode :
      error instanceof Error && 'statusCode' in error && [400, 413, 415].includes(Number(error.statusCode)) ? 422 : 500;
    const code = error instanceof ApiError ? error.code : status === 422 ? 'INVALID_INPUT' : 'INTERNAL_ERROR';
    reply.code(status).send({ error: { code, message: error instanceof ApiError ? error.message : status === 422 ? 'Invalid request' : 'Request failed' } });
  });
  app.setNotFoundHandler((_request, reply) => reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Resource not found' } }));
  const owner = authRoutes(app, db, options);
  cardRoutes(app, db, owner, options.now ?? Date.now);
  walletRoutes(app, db, owner, options.now ?? Date.now);
  return { app };
}
