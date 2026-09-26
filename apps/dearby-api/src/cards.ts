import { randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import type { DB } from './database.js';
import { ApiError, cardInput, id, missing, profileInput, type Card, type Profile } from './validation.js';
export type Owner = (request: FastifyRequest) => string;
export function readCard(db: DB, cardId: string, allowRevoked = false): Card {
  const row = db.prepare('SELECT data, revoked FROM cards WHERE id = ?').get(cardId) as {data:string; revoked:number} | undefined;
  if (!row || (row.revoked && !allowRevoked)) throw missing();
  return JSON.parse(row.data);
}
export function cardRoutes(app: FastifyInstance, db: DB, owner: Owner, now: () => number) {
  function profile(profileId: string): Profile {
    const row = db.prepare('SELECT data FROM profiles WHERE id = ?').get(profileId) as {data:string};
    return JSON.parse(row.data);
  }
  app.get('/v1/profile', async request => profile(owner(request)));
  app.put('/v1/profile', async request => {
    const profileId = owner(request);
    const input = profileInput.parse(request.body);
    const result = {...input,id:profileId,updatedAt:new Date(now()).toISOString()};
    db.prepare('UPDATE profiles SET data = ? WHERE id = ?').run(JSON.stringify(result),profileId);
    return result;
  });
  app.get('/v1/cards', async request => {
    const rows = db.prepare('SELECT data FROM cards WHERE owner_id = ? AND revoked = 0 ORDER BY rowid').all(owner(request)) as {data:string}[];
    return {items:rows.map(row => JSON.parse(row.data))};
  });
  app.post('/v1/cards', async (request, reply) => {
    const profileId = owner(request);
    const input = cardInput.parse(request.body);
    const card = db.transaction(() => {
      const source = profile(profileId);
      const contacts = input.contactIds.map(id => source.contacts.find(c => c.id === id));
      const histories = input.historyIds.map(id => source.histories.find(h => h.id === id));
      if (contacts.some(c => !c) || histories.some(h => !h)) throw new ApiError(422,'INVALID_SELECTION','Selection must belong to your profile');
      // Construct public projection explicitly; never serialize the entire private profile.
      const result: Card = {id:randomUUID(),ownerId:profileId,name:input.name,description:input.description,
        profileName:source.name,job:source.job,introduction:source.introduction,
        contacts:contacts as Profile['contacts'],histories:histories as Profile['histories'],createdAt:new Date(now()).toISOString()};
      db.prepare('INSERT INTO cards (id,owner_id,data) VALUES (?,?,?)').run(result.id,profileId,JSON.stringify(result));
      return result;
    }).immediate();
    return reply.code(201).send(card);
  });
  app.get('/v1/cards/:id', async request => readCard(db,id.parse((request.params as {id:string}).id)));
  app.delete('/v1/cards/:id', async (request, reply) => {
    const profileId = owner(request);
    const card = readCard(db,id.parse((request.params as {id:string}).id));
    if (card.ownerId !== profileId) throw new ApiError(403,'FORBIDDEN','Card owner required');
    db.prepare('UPDATE cards SET revoked = 1 WHERE id = ?').run(card.id);
    return reply.code(204).send();
  });
}
