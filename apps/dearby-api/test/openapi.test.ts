import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {Script,runInNewContext} from 'node:vm';
import {Ajv} from 'ajv';
import {default as formats} from 'ajv-formats';
import {createApp} from '../src/app.js';
import {ApiError,importItem} from '../src/validation.js';
import {fixture} from './helpers.js';

const secret='fixture-only-guest-proxy-secret-for-openapi';
const otpSecret='fixture-only-otp-secret-for-openapi';

test('OpenAPI covers every live API method without adding runtime validators/serializers; docs assets are read-only',async t=>{
  const f=await fixture();
  const {app}=createApp(f.db,{otpSecret,guestProxySecret:secret,sendCode:async()=>{}});
  t.after(async()=>{await app.close();await f.close();});
  const routes=new Set<string>();
  app.addHook('onRoute',route=>{
    if(!route.url.startsWith('/v1/'))return;
    assert.equal(route.schema?.body,undefined);
    assert.equal(route.schema?.params,undefined);
    assert.equal(route.schema?.response,undefined);
    for(const method of [route.method].flat())routes.add(`${method.toLowerCase()} ${route.url.replace(/:id/g,'{id}')}`);
  });
  const response=await app.inject('/docs/json');assert.equal(response.statusCode,200);
  const spec=response.json();assert.equal(spec.openapi,'3.0.3');
  const documented=new Set<string>();const ids=new Set<string>();
  for(const [path,methods] of Object.entries(spec.paths))for(const [method,operation] of Object.entries(methods as Record<string,any>)){
    documented.add(`${method} ${path}`);
    assert.ok(operation.operationId);assert.ok(!ids.has(operation.operationId),operation.operationId);ids.add(operation.operationId);
    assert.ok(operation.responses['500']);assert.ok(operation.responses['422']);
    for(const [status,response] of Object.entries(operation.responses as Record<string,any>))if(method==='head'||status==='204')assert.equal(response.content,undefined);
    if(path.includes('/guest/'))assert.ok(operation.security.every((s:object)=>'GuestProxy' in s));
    else if(path==='/v1/catalog'||(['/v1/cards/{id}','/v1/shares/{id}'].includes(path)&&['get','head'].includes(method))||['/v1/auth/challenges','/v1/auth/sessions'].includes(path))assert.deepEqual(operation.security,[]);
    else assert.deepEqual(operation.security,[{OwnerSession:[]}]);
  }
  assert.equal(routes.size,30);assert.deepEqual(documented,routes);
  assert.match(spec.info.description,/Owner\/auth paths remain 404/);
  assert.equal(spec.components.securitySchemes.GuestProxy.name,'X-Guest-Proxy-Key');
  const example=spec.paths['/v1/wallet/import'].post.requestBody.content['application/json'].schema.example;
  assert.ok(importItem.safeParse(example.items[0]).success);
  assert.equal((await app.inject('/docs/yaml')).statusCode,200);
  const html=await app.inject('/docs/');assert.equal(html.statusCode,200);
  assert.match(html.headers['content-security-policy'] as string,/script-src/);
  const urls=[...html.body.matchAll(/(?:src|href)="([^"#]+)"/g)].map(m=>m[1]).filter(url=>!url.startsWith('data:'));
  assert.ok(urls.length>=3);
  const contents=[response.body,html.body];let initializer='';let css='';
  for(const url of urls){
    assert.ok(!/^https?:/.test(url),'No external documentation assets');
    const path=new URL(url,'http://localhost/docs/').pathname;
    const asset=await app.inject(path);assert.equal(asset.statusCode,200,path);contents.push(asset.body);
    if(path.endsWith('.js')){new Script(asset.body);if(asset.body.includes('SwaggerUIBundle'))initializer=asset.body;}
    if(path.endsWith('.css'))css+=asset.body;
  }
  assert.match(css,/authorization__btn/);
  let config:any;const window:any={location:{href:'http://localhost/docs/'}};
  const document={createElement:()=>({}),querySelector:()=>({appendChild:()=>{}})};
  const bundle=Object.assign((options:unknown)=>{config=options;return {initOAuth:()=>{}};},{presets:{apis:{}},plugins:{DownloadUrl:{}}});
  runInNewContext(initializer,{window,document,SwaggerUIBundle:bundle,SwaggerUIStandalonePreset:{}});window.onload();
  assert.deepEqual(Array.from(config.supportedSubmitMethods),[]);
  assert.equal(config.persistAuthorization,false);assert.equal(config.validatorUrl,null);
  assert.equal(config.tryItOutEnabled,false);assert.notEqual(config.queryConfigEnabled,true);
  for(const content of contents){assert.ok(!content.includes(secret));assert.ok(!content.includes(otpSecret));}
});

test('Real PostgreSQL HTTP successes/errors match OpenAPI, including partial imports, public projections and guest tokens',async t=>{
  const f=await fixture(undefined,secret);t.after(f.close);
  const spec=(await f.app.inject('/docs/json')).json();
  const ajv=new Ajv({strict:false});formats.default(ajv);
  const seen=new Set<string>();
  async function call(method:'GET'|'HEAD'|'POST'|'PUT'|'DELETE',path:string,status:number,body?:unknown,token?:string,guest?:string):Promise<any>{
    const r=await f.app.inject({method,url:'/v1'+path,headers:{...(body!==undefined?{'content-type':'application/json'}:{}),...(token?{authorization:'Bearer '+token}:{}),...(guest!==undefined?{'x-guest-proxy-key':secret,...(guest?{'x-guest-token':guest}:{})}:{})},...(body!==undefined?{payload:JSON.stringify(body)}:{})});
    assert.equal(r.statusCode,status,`${method} ${path}: ${r.body}`);assert.equal(r.headers['cache-control'],'no-store');
    const template=('/v1'+path).replace(/\/[0-9a-f-]{36}(?=$|\/)/,'/{id}');
    const response=spec.paths[template][method.toLowerCase()].responses[String(status)];assert.ok(response,`${method} ${template} ${status}`);
    seen.add(`${method} ${template}`);
    if(method==='HEAD'||status===204){assert.equal(r.body,'');return null;}
    const validate=ajv.compile(response.content['application/json'].schema);const data=r.json();
    assert.ok(validate(data),JSON.stringify(validate.errors));return data;
  }
  const challenge=await call('POST','/auth/challenges',202,{email:'docs-fixture@example.com'});
  await call('POST','/auth/challenges',429,{email:'docs-fixture@example.com'});
  const session=await call('POST','/auth/sessions',200,{challengeId:challenge.challengeId,code:f.codes.get('docs-fixture@example.com')});
  const owner=session.sessionToken;const other=await f.login('other-docs@example.com');
  await call('GET','/profile',401);await call('GET','/profile',200,undefined,owner);
  const contact={id:randomUUID(),kind:'email',label:'Example',value:'fictional@example.com'};
  await call('PUT','/profile',200,{name:'Fixture',job:'Tester',introduction:'Example only',contacts:[contact,{id:randomUUID(),kind:'phone',label:'Example',value:'+82 10-0000-0000'}],histories:[]},owner);
  await call('PUT','/profile',422,{unexpected:true},owner);
  const card=await call('POST','/cards',201,{name:'Example',description:'Fixture',contactIds:[contact.id],historyIds:[]},owner);
  assert.equal(card.contacts[0].value,contact.value);assert.equal(card.profileName,'Fixture');
  await call('GET','/cards',200,undefined,owner);await call('GET',`/cards/${card.id}`,200);
  const organization=randomUUID(),program=randomUUID();
  await f.admin.query('INSERT INTO public.catalog_organizations(id,name) VALUES($1,$2)',[organization,'Documentation fixture']);
  await f.admin.query('INSERT INTO public.catalog_programs(id,organization_id,title) VALUES($1,$2,$3)',[program,organization,'Documentation fixture']);
  const activity=randomUUID();
  await f.admin.query('INSERT INTO public.catalog_activities(id,program_id,organization_id,title,official_url,publication_status) VALUES($1,$2,$3,$4,$5,$6)',[activity,program,organization,'Example activity','https://example.com/fixture','published']);
  const catalog=await call('GET','/catalog',200);assert.equal(catalog.activities.length,1);
  await call('POST',`/cards/${card.id}/shares`,422,{activityIds:[randomUUID()]},owner);
  await call('POST',`/cards/${card.id}/shares`,403,{activityIds:[]},other.sessionToken);
  const share=await call('POST',`/cards/${card.id}/shares`,201,{activityIds:[activity]},owner);
  await call('GET',`/shares/${share.id}`,200);await call('GET',`/shares/${randomUUID()}`,404);
  await call('GET','/wallet',200,undefined,owner);
  await call('PUT',`/wallet/shares/${share.id}`,201,undefined,other.sessionToken);
  await call('PUT',`/wallet/shares/${share.id}`,200,undefined,other.sessionToken);
  await call('PUT',`/wallet/shares/${share.id}`,422,undefined,owner);
  await call('PUT',`/wallet/shares/${randomUUID()}`,404,undefined,other.sessionToken);
  const input={cardId:card.id,recipientProfileId:other.profileId,context:{activityId:null,label:'Meeting'},requestId:randomUUID()};
  await call('POST','/exchanges',201,input,owner);
  await call('POST','/exchanges',409,{...input,context:{activityId:null,label:'Changed'}},owner);
  const imported=await call('POST','/wallet/import',200,{items:[{cardId:card.id,context:input.context,savedAt:'2026-09-01T09:00:00.000Z'},null]},other.sessionToken);
  assert.deepEqual(imported.items.map((i:any)=>i.status),['alreadySaved','failed']);
  await call('GET','/wallet',200,undefined,other.sessionToken);
  await call('GET','/guest/cards',403);await call('GET','/guest/cards',401,undefined,undefined,'');
  const guest=await call('PUT',`/guest/cards/${card.id}`,201,undefined,undefined,'');
  await call('PUT',`/guest/cards/${card.id}`,200,undefined,undefined,guest.guestToken);
  const shared=await call('PUT',`/guest/shares/${share.id}`,201,undefined,undefined,'');
  await call('PUT',`/guest/shares/${share.id}`,200,undefined,undefined,guest.guestToken);
  await call('PUT',`/guest/shares/${randomUUID()}`,404,undefined,undefined,guest.guestToken);
  assert.equal((await call('GET','/guest/cards',200,undefined,undefined,guest.guestToken)).shares.length,1);
  const handoff=await call('POST','/guest/handoffs',201,undefined,undefined,shared.guestToken);
  await call('POST','/guest/handoffs/redeem',422,{},undefined,'');
  assert.deepEqual(await call('POST','/guest/handoffs/redeem',200,{code:handoff.code},undefined,''),{guestToken:shared.guestToken});
  await call('POST','/guest/handoffs/redeem',404,{code:handoff.code},undefined,'');
  await call('DELETE','/guest/session',204,undefined,undefined,shared.guestToken);
  for(const path of ['/catalog','/profile','/cards',`/cards/${card.id}`,`/shares/${share.id}`,'/wallet','/guest/cards'])await call('HEAD',path,200,undefined,owner,guest.guestToken);
  await call('DELETE',`/guest/cards/${card.id}`,204,undefined,undefined,guest.guestToken);
  await call('DELETE','/guest/session',204,undefined,undefined,guest.guestToken);
  await call('GET','/guest/cards',401,undefined,undefined,guest.guestToken);
  await call('DELETE',`/cards/${card.id}`,403,undefined,other.sessionToken);
  await call('DELETE',`/cards/${card.id}`,204,undefined,owner);await call('GET',`/cards/${card.id}`,404);await call('GET',`/shares/${share.id}`,404);
  await call('DELETE','/auth/session',204,undefined,owner);
  assert.equal(seen.size,30);
  // Dependency failures keep the established sanitized error contract.
  for(const failure of [new ApiError(503,'CATALOG_UNAVAILABLE','Catalog unavailable'),Error('fixture-private-storage-detail')]){
    const {app}=createApp(f.db,{otpSecret,sendCode:async()=>{},catalogReader:async()=>{throw failure;}});
    try{const r=await app.inject('/v1/catalog');const status=failure instanceof ApiError?503:500;assert.equal(r.statusCode,status);
      assert.ok(ajv.validate(spec.paths['/v1/catalog'].get.responses[status].content['application/json'].schema,r.json()));assert.ok(!r.body.includes('fixture-private-storage-detail'));
    }finally{await app.close();}
  }
});
