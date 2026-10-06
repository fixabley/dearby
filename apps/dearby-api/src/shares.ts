import {documented,ownerOnly,ownerSecurity,idParams,publicShareSchema} from './openapi.js';
import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import type { Catalog } from './catalog.js';
import { readCard, type Owner } from './cards.js';
import { write, type DB, type Transaction } from './postgres.js';
import { ApiError, cardShareSchema, id, missing, shareInput, type Card, type CardShare } from './validation.js';

// A share of a withdrawn card is unavailable exactly like the card itself.
export async function readShare(db: DB | Transaction, shareId: string): Promise<{share: CardShare; card: Card}> {
  const row = await db.cardShare.findUnique({where:{id:shareId}});
  if (!row) throw missing();
  const card = await readCard(db, row.cardId);
  return {share:{id:row.id,cardId:row.cardId,activities:JSON.parse(row.activities),createdAt:row.createdAt}, card};
}
export function shareRoutes(app: FastifyInstance, db: DB, owner: Owner, catalog: () => Promise<Catalog>, now: () => number) {
  app.post('/v1/cards/:id/shares', documented({operationId:'createCardShare',summary:'Record a share of your card with selected catalog activities',tag:'Cards',description:ownerOnly+' 0-10 unique activity IDs that exist in the current published catalog (otherwise 422; catalog failure 503). Activities are a {id,title} snapshot that never follows later catalog edits, and are the sharer\'s choice, not participation certification. Withdrawn/missing cards 404, other owners 403.',security:ownerSecurity,params:idParams,body:shareInput,bodyExample:{activityIds:['00000000-0000-4000-8000-000000000001']},responses:{201:cardShareSchema},errors:[401,403,404,503]}), async (request, reply) => {
    const profileId = await owner(request);
    const cardId = id.parse((request.params as {id:string}).id).toLowerCase();
    const activityIds = shareInput.parse(request.body).activityIds.map(a => a.toLowerCase());
    const card = await readCard(db, cardId);
    if (card.ownerId !== profileId) throw new ApiError(403,'FORBIDDEN','Card owner required');
    const titles = new Map(activityIds.length ? (await catalog()).activities.map(a => [a.id.toLowerCase(), a.title]) : []);
    if (activityIds.some(a => !titles.has(a))) throw new ApiError(422,'INVALID_SELECTION','Activities must exist in the current catalog');
    const share: CardShare = {id:randomUUID(),cardId,activities:activityIds.map(a => ({id:a,title:titles.get(a)!})),createdAt:new Date(now()).toISOString()};
    await write(db, async tx => {
      await readCard(tx, cardId); // Withdrawal may have committed while the catalog was read.
      await tx.cardShare.create({data:{id:share.id,cardId,activities:JSON.stringify(share.activities),createdAt:share.createdAt}});
    });
    return reply.code(201).send(share);
  });
  app.get('/v1/shares/:id', documented({operationId:'getPublicShare',summary:'Read a card share and its public card snapshot',tag:'Cards',description:'Public; intended for GET/HEAD at ingress after root rollout. UUID is case-normalized. Missing shares and every share of a withdrawn card return 404. The share ID identifies public content, never a visitor or wallet.',params:idParams,responses:{200:publicShareSchema},errors:[404]}), async request => readShare(db,id.parse((request.params as {id:string}).id).toLowerCase()));
}
