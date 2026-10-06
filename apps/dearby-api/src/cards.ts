import {documented,ownerOnly,ownerSecurity,idParams,cardListSchema} from './openapi.js';
import { randomUUID } from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import { write, type DB, type Transaction } from './postgres.js';
import { ApiError, cardInput, id, missing, profileSchema, profileUpdate, cardSchema, type Card, type Profile } from './validation.js';
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
  app.get('/v1/profile', documented({operationId:'getProfile',summary:'Read your private profile',tag:'Profiles',description:ownerOnly+' Never returned by public-card or guest-wallet routes.',security:ownerSecurity,responses:{200:profileSchema},errors:[401]}), async request => profile(await owner(request)));
  app.put('/v1/profile', documented({operationId:'updateProfile',summary:'Replace your private profile',tag:'Profiles',description:ownerOnly+' At least one phone (digits, +, -, spaces; 8-15 digits) and one email contact are required, otherwise 422 INVALID_INPUT with message "Invalid contacts". Previously stored profiles are still returned as stored. Contact/history IDs must be unique. History startDate is a required YYYY-MM-DD date. End dates cannot precede start dates; contact values reject control characters and non-HTTPS URI schemes. Existing card snapshots remain unchanged.',security:ownerSecurity,body:profileUpdate,responses:{200:profileSchema},errors:[401]}), async request => {
    const profileId = await owner(request);
    const parsed = profileUpdate.safeParse(request.body);
    if (!parsed.success) throw new ApiError(422,'INVALID_INPUT',parsed.error.issues.some(i => i.path[0] === 'contacts') ? 'Invalid contacts' : 'Invalid request');
    const input = parsed.data;
    const result = {...input,id:profileId,updatedAt:new Date(now()).toISOString()};
    await write(db, tx => tx.profile.update({where:{id:profileId},data:{data:JSON.stringify(result)}}));
    return result;
  });
  app.get('/v1/cards', documented({operationId:'listOwnerCards',summary:'List your non-withdrawn cards',tag:'Cards',description:ownerOnly+' Preserves creation order.',security:ownerSecurity,responses:{200:cardListSchema},errors:[401]}), async request => {
    const rows = await db.card.findMany({where:{ownerId:await owner(request),revoked:false},orderBy:{ordinal:'asc'}});
    return {items:rows.map(row => JSON.parse(row.data))};
  });
  app.post('/v1/cards', documented({operationId:'createCard',summary:'Create an immutable public card projection',tag:'Cards',description:ownerOnly+' Only selected contact/history fields are copied. IDs must be unique and belong to your profile; invalid selections return 422. Private email/account data is never copied automatically.',security:ownerSecurity,body:cardInput,responses:{201:cardSchema},errors:[401]}), async (request, reply) => {
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
  app.get('/v1/cards/:id', documented({operationId:'getPublicCard',summary:'Read a public card snapshot',tag:'Cards',description:'Public, also permitted at current nginx ingress. UUID is case-normalized and checked against the database. Missing/withdrawn cards return 404. Path ID identifies public content, never a private profile or visitor wallet.',params:idParams,responses:{200:cardSchema},errors:[404]}), async request => readCard(db,id.parse((request.params as {id:string}).id).toLowerCase()));
  app.delete('/v1/cards/:id', documented({operationId:'withdrawCard',summary:'Withdraw your public card',tag:'Cards',description:ownerOnly+' Only the owner can withdraw. The snapshot/history remains stored but is no longer publicly returned. Missing/already-withdrawn cards return 404.',security:ownerSecurity,params:idParams,responses:{204:null},errors:[401,403,404]}), async (request, reply) => {
    const profileId = await owner(request);
    const card = await readCard(db,id.parse((request.params as {id:string}).id).toLowerCase());
    if (card.ownerId !== profileId) throw new ApiError(403,'FORBIDDEN','Card owner required');
    await write(db, tx => tx.card.update({where:{id:card.id},data:{revoked:true}}));
    return reply.code(204).send();
  });
}
