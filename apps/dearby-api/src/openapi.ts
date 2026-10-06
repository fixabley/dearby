import swagger from '@fastify/swagger';
import swaggerUI from '@fastify/swagger-ui';
import type { FastifyInstance, FastifySchema } from 'fastify';
import { z } from 'zod';
import { cardSchema, cardShareSchema, context, id, shareActivity } from './validation.js';
export const tokenSchema = z.string().regex(/^[A-Za-z0-9_-]{43}$/);
export const idParams = z.strictObject({id});
export const cardListSchema = z.strictObject({items:z.array(cardSchema)});
export const walletSchema = z.strictObject({items:z.array(z.strictObject({
  id,card:cardSchema,context,receivedAt:z.iso.datetime({offset:true}),reciprocal:z.boolean(),
})),shares:z.array(z.strictObject({receiptId:id,cardId:id,shareId:id,activities:z.array(shareActivity).max(10),savedAt:z.iso.datetime()}))});
export const walletShareSavedSchema = z.strictObject({receiptId:id,cardId:id,shareId:id,status:z.enum(['saved','alreadySaved'])});
export const importResultSchema = z.strictObject({items:z.array(z.strictObject({
  cardId:z.string(),status:z.enum(['imported','alreadySaved','failed']),receiptId:id.nullable(),
}))});
export const publicShareSchema = z.strictObject({share:cardShareSchema,card:cardSchema});
export const guestShareSchema = z.strictObject({cardId:id,shareId:id,activities:z.array(shareActivity).max(10),savedAt:z.iso.datetime()});
export const guestListSchema = z.strictObject({items:z.array(cardSchema),shares:z.array(guestShareSchema)});
export const guestShareSavedSchema = z.strictObject({cardId:id,shareId:id,status:z.enum(['saved','alreadySaved'])});
export const guestShareCreatedSchema = guestShareSavedSchema.extend({status:z.literal('saved'),guestToken:tokenSchema.describe('Server-proxy consumption only. Next sets the HttpOnly cookie and must omit this field from browser JSON.')});
export const handoffSchema = z.strictObject({code:tokenSchema.describe('One-time handoff code. Server-proxy consumption only; never log it.'),expiresAt:z.iso.datetime()});
export const handoffRedeemedSchema = z.strictObject({guestToken:tokenSchema.describe('Same token as the issuing session. Next sets the HttpOnly cookie and must omit this field from browser JSON.')});
export const guestSavedSchema = z.strictObject({cardId:id,status:z.enum(['saved','alreadySaved'])});
export const guestCreatedSchema = z.strictObject({cardId:id,status:z.literal('saved'),guestToken:tokenSchema.describe('Server-proxy consumption only. Next sets the HttpOnly cookie and must omit this field from browser JSON.')});
const errorSchema = z.strictObject({error:z.strictObject({code:z.string(),message:z.string()})});
const descriptions:Record<number,string> = {
  401:'UNAUTHORIZED / INVALID_CODE / GUEST_SESSION_INVALID: missing, expired, consumed or revoked credentials. Invalid guest tokens never create a replacement session.',
  403:'FORBIDDEN: owner required or missing/incorrect trusted server proxy credential.',
  404:'NOT_FOUND: resource missing or public card withdrawn (including every share of a withdrawn card).',
  409:'IDEMPOTENCY_CONFLICT / GUEST_CAPACITY_EXCEEDED / WALLET_CAPACITY_EXCEEDED: conflicting replay or persistent storage limit.',
  422:'INVALID_INPUT / INVALID_SELECTION / INVALID_RECIPIENT: invalid JSON, media type, body size, Zod input or domain selection (including activities missing from the current catalog).',
  429:'RATE_LIMITED: request, creation or OTP quota exceeded.',
  500:'INTERNAL_ERROR: sanitized storage/internal failure; never an empty successful response.',
  503:'CATALOG_UNAVAILABLE / MAIL_UNAVAILABLE / GUEST_UNAVAILABLE: dependency unavailable or not configured.',
};
export const ownerSecurity = [{OwnerSession:[]}];
export const guestSecurity = [{GuestProxy:[],GuestSession:[]}];
export const guestProxySecurity = [{GuestProxy:[]}];
export const guestFirstSaveSecurity:Record<string,string[]>[] = [{GuestProxy:[]},{GuestProxy:[],GuestSession:[]}];
export const ownerOnly = 'Owner bearer session required. Currently not exposed by the public nginx proxy (404 there); available only through a permitted direct/local API connection.';
export const guestOnly = 'Trusted Next same-origin server proxy only. X-Guest-Proxy-Key is a server-only secret: never put it in a browser, mobile app, Swagger authorization dialog, example, URL or client bundle. X-Guest-Token is forwarded server-to-server from __Host-dearby_guest. Next owns HttpOnly/Secure/SameSite=Lax/Path=/ cookie renewal (400 days, same token); API stores only the digest, no automatic guest expiry. A card ID alone never authenticates a wallet.';
export const noStore = {type:'string',enum:['no-store']};
export function jsonSchema(schema:z.ZodType,io:'input'|'output'='output') {
  const result=z.toJSONSchema(schema,{target:'openapi-3.0',io});
  delete result.$schema;
  return result;
}
type Documentation = {
  operationId:string;summary:string;description:string;tag:string;
  security?:Record<string,string[]>[];
  body?:z.ZodType;params?:z.ZodType;bodyExample?:unknown;
  responses:Record<number,z.ZodType|null>;
  errors?:number[];
};
// This is a Swagger transform only: no schema is attached to the live Fastify route.
// Zod remains the validator; Fastify never gets an extra body validator or serializer.
export function documented(d:Documentation) {
  const response:Record<string,unknown>={};
  for(const [status,schema] of Object.entries(d.responses)) response[status]=schema ? {
    ...jsonSchema(schema),description:status==='201'?'Created':status==='202'?'Accepted after successful email dispatch':'Success',
    headers:{'Cache-Control':noStore},
  } : {type:'null',description:'No content',headers:{'Cache-Control':noStore}};
  for(const status of new Set([422,500,...(d.errors??[])])) response[status]={...jsonSchema(errorSchema),description:descriptions[status],headers:{'Cache-Control':noStore}};
  const schema:FastifySchema={operationId:d.operationId,summary:d.summary,description:d.description,tags:[d.tag],security:d.security??[],
    ...(d.body?{body:{...jsonSchema(d.body,'input'),...(d.bodyExample?{example:d.bodyExample}:{})}}:{}),...(d.params?{params:jsonSchema(d.params,'input')}:{}),response};
  return {config:{swaggerTransform:({url,route}:{url:string;route:{method:string|string[]}})=>{
    const result=structuredClone(schema);
    if(route.method==='HEAD'){
      result.operationId='head'+d.operationId[0].toUpperCase()+d.operationId.slice(1);
      result.description+=' HEAD returns the same status and headers without a response body.';
      result.response=Object.fromEntries(Object.entries(response).map(([status,value])=>[status,{...(value as object),type:'null'}]));
    }
    return {url,schema:result};
  }}};
}
export async function registerDocumentation(app:FastifyInstance) {
  await app.register(swagger,{
    openapi:{openapi:'3.0.3',info:{title:'Dearby API',version:'1.0.0',description:
      'Read-only API reference. Documents the full Fastify API; current public nginx exposes catalog, public card reads and trusted guest proxy paths only. Owner/auth paths remain 404 at public ingress. Runtime Zod also checks duplicate IDs, date ordering, contact URL/control characters and context exclusivity; JSON Schema cannot express every refinement. All API responses use Cache-Control: no-store. No production credentials or user data are included.'},
      servers:[{url:'/',description:'Same origin; availability is subject to the ingress policy above.'}],
      components:{securitySchemes:{
        OwnerSession:{type:'http',scheme:'bearer',bearerFormat:'Opaque 43-character base64url token',description:'API owner session, expires after 30 days; independent of Supabase administrator Auth.'},
        GuestProxy:{type:'apiKey',in:'header',name:'X-Guest-Proxy-Key',description:'SERVER-ONLY. Never enter or expose this secret in a browser. Used only by the trusted Next server.'},
        GuestSession:{type:'apiKey',in:'header',name:'X-Guest-Token',description:'Opaque visitor token forwarded only by the trusted server proxy. No server expiry; explicit revocation is final.'},
      }}},
    exposeHeadRoutes:true,
    transform:({schema,url})=>({url,schema:{...schema,hide:true}}),
  });
  await app.register(swaggerUI,{routePrefix:'/docs',staticCSP:true,
    uiConfig:{supportedSubmitMethods:[],persistAuthorization:false,validatorUrl:null,tryItOutEnabled:false},
    // Hide both global and operation auth controls, including their secret input dialogs.
    theme:{css:[{filename:'read-only.css',content:'.swagger-ui .auth-wrapper,.swagger-ui .authorization__btn{display:none!important}'}]},
  });
}
export const challengeResponse = z.strictObject({challengeId:id,expiresAt:z.iso.datetime()});
export const sessionResponse = z.strictObject({sessionToken:tokenSchema,profileId:id});
export const exchangeResponse = z.strictObject({receiptId:id,deliveredAt:z.iso.datetime()});
