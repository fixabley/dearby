import { randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { write, type DB, type Transaction } from './postgres.js';
import { ApiError, cardInput, id, missing, profileInput, type Card, type Profile } from './validation.js';
export type Owner = (request: FastifyRequest) => Promise<string>;
export async function readCard(db: DB | Transaction, cardId: string): Promise<Card> {
  const row = await db.card.findUnique({where:{id:cardId}});
  if (!row || row.revoked) throw missing();
  return JSON.parse(row.data);
}
export function cardRoutes(app: FastifyInstance, db: DB, owner: Owner, now: () => number) {
  async function profile(profileId: string): Promise<Profile> {
    const row = await db.profile.findUniqueOrThrow({where:{id:profileId}});
    return JSON.parse(row.data);
  }
  app.get('/v1/profile', async request => profile(await owner(request)));
  app.put('/v1/profile', async request => {
    const profileId = await owner(request);
    const input = profileInput.parse(request.body);
    const result = {...input,id:profileId,updatedAt:new Date(now()).toISOString()};
    await write(db, tx => tx.profile.update({where:{id:profileId},data:{data:JSON.stringify(result)}}));
    return result;
  });
  app.get('/v1/cards', async request => {
    const rows = await db.card.findMany({where:{ownerId:await owner(request),revoked:false},orderBy:{ordinal:'asc'}});
    return {items:rows.map(row => JSON.parse(row.data))};
  });
  app.post('/v1/cards', async (request, reply) => {
    const profileId = await owner(request);
    const input = cardInput.parse(request.body);
    const card = await write(db, async tx => {
      const source: Profile = JSON.parse((await tx.profile.findUniqueOrThrow({where:{id:profileId}})).data);
      const contacts = input.contactIds.map(id => source.contacts.find(c => c.id === id));
      const histories = input.historyIds.map(id => source.histories.find(h => h.id === id));
      if (contacts.some(c => !c) || histories.some(h => !h)) throw new ApiError(422,'INVALID_SELECTION','Selection must belong to your profile');
      // Construct public projection explicitly; never serialize the entire private profile.
      const result: Card = {id:randomUUID(),ownerId:profileId,name:input.name,description:input.description,
        profileName:source.name,job:source.job,introduction:source.introduction,
        contacts:contacts as Profile['contacts'],histories:histories as Profile['histories'],createdAt:new Date(now()).toISOString()};
      await tx.card.create({data:{id:result.id,ownerId:profileId,data:JSON.stringify(result)}});
      return result;
    });
    return reply.code(201).send(card);
  });
  app.get('/v1/cards/:id', async request => readCard(db,id.parse((request.params as {id:string}).id).toLowerCase()));
  app.delete('/v1/cards/:id', async (request, reply) => {
    const profileId = await owner(request);
    const card = await readCard(db,id.parse((request.params as {id:string}).id).toLowerCase());
    if (card.ownerId !== profileId) throw new ApiError(403,'FORBIDDEN','Card owner required');
    await write(db, tx => tx.card.update({where:{id:card.id},data:{revoked:true}}));
    return reply.code(204).send();
  });
}
