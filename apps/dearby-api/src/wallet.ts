import {documented,ownerOnly,ownerSecurity,walletSchema,importResultSchema,exchangeResponse} from './openapi.js';
import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { write, type DB } from './postgres.js';
import { readCard, type Owner } from './cards.js';
import { ApiError, exchangeInput, importItem, importEnvelope, missing } from './validation.js';
export function walletRoutes(app: FastifyInstance, db: DB, owner: Owner, now: () => number) {
  app.get('/v1/wallet', documented({operationId:'getOwnerWallet',summary:'List cards delivered or imported to your wallet',tag:'Wallet',description:ownerOnly+' Withdrawn cards are filtered. Ordered by receivedAt then stored order. Reciprocal is false for self; historical return deliveries count even when later withdrawn.',security:ownerSecurity,responses:{200:walletSchema},errors:[401]}), async request => {
    const profileId = await owner(request);
    const rows = await db.receipt.findMany({where:{recipientId:profileId,card:{revoked:false}},include:{card:true},orderBy:[{receivedAt:'asc'},{ordinal:'asc'}]});
    const ownerIds = [...new Set(rows.map(row => row.card.ownerId).filter(id => id !== profileId))];
    // Historical reciprocal deliveries count even when the sender later withdraws that card.
    const reciprocalRows = ownerIds.length ? await db.receipt.findMany({
      where:{recipientId:{in:ownerIds},card:{ownerId:profileId}},select:{recipientId:true},
    }) : [];
    const reciprocalOwners = new Set(reciprocalRows.map(row => row.recipientId));
    return {items:rows.map(row => ({id:row.id,card:JSON.parse(row.card.data),context:JSON.parse(row.context),receivedAt:row.receivedAt,
      reciprocal:row.card.ownerId !== profileId && reciprocalOwners.has(row.card.ownerId),
    }))};
  });
  app.post('/v1/wallet/import', documented({operationId:'importOwnerWallet',summary:'Import saved public card references with per-item outcomes',tag:'Wallet',description:ownerOnly+' Envelope accepts up to 100 unknown items so invalid individual items produce failed results, not whole-request 422. Each valid item requires {cardId: UUID, context: {activityId: UUID|null, label: nonempty string|null}, savedAt: ISO UTC date-time}. Context chooses activity or label, never both. Original context/date preserved; duplicates are alreadySaved; missing/withdrawn/invalid/storage-failed items are failed with null receiptId. Successful siblings remain committed.',security:ownerSecurity,body:importEnvelope,bodyExample:{items:[{cardId:'00000000-0000-4000-8000-000000000001',context:{activityId:null,label:'Example meeting'},savedAt:'2026-09-01T09:00:00.000Z'}]},responses:{200:importResultSchema},errors:[401]}), async request => {
    const profileId = await owner(request);
    const {items} = importEnvelope.parse(request.body);
    const results = [];
    for (const raw of items) {
      const cardId = raw && typeof raw === 'object' && 'cardId' in raw && typeof raw.cardId === 'string' ? raw.cardId : '';
      const parsed = importItem.safeParse(raw);
      if (!parsed.success) { results.push({cardId,status:'failed',receiptId:null}); continue; }
      try {
        results.push(await write(db, async tx => {
          const input = parsed.data;
          await readCard(tx,input.cardId);
          const existing = await tx.receipt.findFirst({where:{recipientId:profileId,cardId:input.cardId},orderBy:{ordinal:'asc'}});
          if (existing) return {cardId,status:'alreadySaved',receiptId:existing.id};
          const receiptId = randomUUID();
          await tx.receipt.create({data:{id:receiptId,recipientId:profileId,cardId:input.cardId,context:JSON.stringify(input.context),receivedAt:input.savedAt}});
          return {cardId,status:'imported',receiptId};
        }));
      } catch { results.push({cardId,status:'failed',receiptId:null}); }
    }
    return {items:results};
  });
  app.post('/v1/exchanges', documented({operationId:'createExchange',summary:'Deliver your card to another profile',tag:'Wallet',description:ownerOnly+' Card must belong to sender; recipient must exist and differ from sender. Context chooses activity or label, never both. Same requestId/input replays the original receipt, including after withdrawal; conflicting input returns 409. New requestIds intentionally create repeated delivery history.',security:ownerSecurity,body:exchangeInput,responses:{201:exchangeResponse},errors:[401,403,404,409]}), async (request, reply) => {
    const senderId = await owner(request);
    const input = exchangeInput.parse(request.body);
    const body = JSON.stringify(input);
    const result = await write(db, async tx => {
      const prior = await tx.exchange.findUnique({where:{senderId_requestId:{senderId,requestId:input.requestId}}});
      if (prior) {
        if (prior.body !== body) throw new ApiError(409,'IDEMPOTENCY_CONFLICT','Request ID already used with different input');
        return {receiptId:prior.receiptId,deliveredAt:prior.deliveredAt};
      }
      const card = await readCard(tx,input.cardId);
      if (card.ownerId !== senderId) throw new ApiError(403,'FORBIDDEN','Card owner required');
      if (senderId === input.recipientProfileId) throw new ApiError(422,'INVALID_RECIPIENT','Choose another profile');
      if (!await tx.profile.findUnique({where:{id:input.recipientProfileId}})) throw missing();
      const receiptId = randomUUID(); const deliveredAt = new Date(now()).toISOString();
      await tx.receipt.create({data:{id:receiptId,recipientId:input.recipientProfileId,cardId:input.cardId,context:JSON.stringify(input.context),receivedAt:deliveredAt}});
      await tx.exchange.create({data:{senderId,requestId:input.requestId,body,receiptId,deliveredAt}});
      return {receiptId,deliveredAt};
    });
    return reply.code(201).send(result);
  });
}
