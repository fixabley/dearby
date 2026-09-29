import {registerDocumentation,documented} from './openapi.js';
import Fastify from 'fastify';
import { guestRoutes } from './guest.js';
import {catalogSchema,type Catalog} from './catalog.js';
import { prismaCatalog } from './catalog-prisma.js';
import { ZodError } from 'zod';
import type { DB } from './postgres.js';
import { ApiError } from './validation.js';
import { walletRoutes } from './wallet.js';
import { cardRoutes } from './cards.js';
import { authRoutes, type AuthOptions } from './auth.js';

export function createApp(db: DB, options: AuthOptions & {catalogReader?: () => Promise<Catalog>; guestProxySecret?: string}) {
  const app = Fastify({ logger: false, bodyLimit: 65536, trustProxy: false });
  app.addHook('onSend', async (_request, reply) => { reply.header('Cache-Control', 'no-store'); });
  app.setErrorHandler((error, _request, reply) => {
    const status = error instanceof ZodError ? 422 : error instanceof ApiError ? error.statusCode :
      error instanceof Error && 'statusCode' in error && [400, 413, 415].includes(Number(error.statusCode)) ? 422 : 500;
    const code = error instanceof ApiError ? error.code : status === 422 ? 'INVALID_INPUT' : 'INTERNAL_ERROR';
    reply.code(status).send({ error: { code, message: error instanceof ApiError ? error.message : status === 422 ? 'Invalid request' : 'Request failed' } });
  });
  app.setNotFoundHandler((_request, reply) => reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Resource not found' } }));
  void app.register(async api => {
    await registerDocumentation(api);
    api.get('/v1/catalog',documented({operationId:'getCatalog',summary:'Read the public activity catalog',tag:'Catalog',
      description:'Public read; current nginx permits GET/HEAD. Returns only published data; draft/hidden data is excluded. Recalculates freshness and deadlines. Read failures return 503. An empty published catalog is valid 200.',responses:{200:catalogSchema},errors:[503]}),
      async () => options.catalogReader ? options.catalogReader() : prismaCatalog(db)());
    const owner = authRoutes(api,db,options);
    cardRoutes(api,db,owner,options.now??Date.now);
    walletRoutes(api,db,owner,options.now??Date.now);
    guestRoutes(api,db,options.guestProxySecret,options.now??Date.now);
  });
  return { app };
}
