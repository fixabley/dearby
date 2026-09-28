import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { DB } from './database.js';
import { readCard, type Owner } from './cards.js';
import { ApiError, exchangeInput, importItem, missing } from './validation.js';
export function walletRoutes(app: FastifyInstance, db: DB, owner: Owner, now: () => number) {
  app.get('/v1/wallet', async request => {
    const profileId = owner(request);
    const rows = db.prepare(`SELECT r.*, c.owner_id FROM receipts r JOIN cards c ON c.id = r.card_id
      WHERE r.recipient_id = ? AND c.revoked = 0 ORDER BY r.received_at, r.rowid`).all(profileId) as
      {id:string;card_id:string;owner_id:string;context:string;received_at:string}[];
    return {items:rows.map(row => ({id:row.id,card:readCard(db,row.card_id),context:JSON.parse(row.context),receivedAt:row.received_at,
      reciprocal: row.owner_id !== profileId && Boolean(db.prepare(`SELECT 1 FROM receipts r JOIN cards c ON c.id = r.card_id
        WHERE r.recipient_id = ? AND c.owner_id = ? LIMIT 1`).get(row.owner_id,profileId)),
    }))};
  });
  app.post('/v1/wallet/import', async request => {
    const profileId = owner(request);
    // Validate envelope first, individual content independently to preserve partial success.
    const {items} = z.strictObject({items:z.array(z.unknown()).max(100)}).parse(request.body);
    return {items:items.map(raw => {
      const cardId = raw && typeof raw === 'object' && 'cardId' in raw && typeof raw.cardId === 'string' ? raw.cardId : '';
      const parsed = importItem.safeParse(raw);
      if (!parsed.success) return {cardId,status:'failed',receiptId:null};
      try {
        return db.transaction(() => {
          const input = parsed.data;
          readCard(db,input.cardId);
          const existing = db.prepare('SELECT id FROM receipts WHERE recipient_id = ? AND card_id = ? ORDER BY rowid LIMIT 1').get(profileId,input.cardId) as {id:string} | undefined;
          if (existing) return {cardId,status:'alreadySaved',receiptId:existing.id};
          const receiptId = randomUUID();
          db.prepare('INSERT INTO receipts VALUES (?,?,?,?,?)').run(receiptId,profileId,input.cardId,JSON.stringify(input.context),input.savedAt);
          return {cardId,status:'imported',receiptId};
        }).immediate();
      } catch {
        // Do not swallow an item failure as import success or abort successful siblings.
        return {cardId,status:'failed',receiptId:null};
      }
    })};
  });
  app.post('/v1/exchanges', async (request, reply) => {
    const senderId = owner(request);
    const input = exchangeInput.parse(request.body);
    // Zod schema constructs canonical property order, including nested context.
    const body = JSON.stringify(input);
    const result = db.transaction(() => {
      const prior = db.prepare('SELECT * FROM exchanges WHERE sender_id = ? AND request_id = ?').get(senderId,input.requestId) as {body:string;receipt_id:string;delivered_at:string} | undefined;
      if (prior) {
        if (prior.body !== body) throw new ApiError(409,'IDEMPOTENCY_CONFLICT','Request ID already used with different input');
        return {receiptId:prior.receipt_id,deliveredAt:prior.delivered_at};
      }
      const card = readCard(db,input.cardId);
      if (card.ownerId !== senderId) throw new ApiError(403,'FORBIDDEN','Card owner required');
      if (senderId === input.recipientProfileId) throw new ApiError(422,'INVALID_RECIPIENT','Choose another profile');
      if (!db.prepare('SELECT id FROM profiles WHERE id = ?').get(input.recipientProfileId)) throw missing();
      const receiptId = randomUUID();
      const deliveredAt = new Date(now()).toISOString();
      db.prepare('INSERT INTO receipts VALUES (?,?,?,?,?)').run(receiptId,input.recipientProfileId,input.cardId,JSON.stringify(input.context),deliveredAt);
      db.prepare('INSERT INTO exchanges VALUES (?,?,?,?,?)').run(senderId,input.requestId,body,receiptId,deliveredAt);
      return {receiptId,deliveredAt};
    }).immediate();
    return reply.code(201).send(result);
  });
}
