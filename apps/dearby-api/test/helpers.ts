import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import pg from 'pg';
import {readFileSync} from 'node:fs';
import { openPostgres,verifyRuntimeRole } from '../src/postgres.js';
import { createApp } from '../src/app.js';
import type { SendCode } from '../src/mail.js';
export async function fixture(send?:SendCode,guestProxySecret?:string) {
  const source=process.env.PG_TEST_ADMIN_URL;
  if(process.env.NODE_ENV!=='test'||!source)throw Error('Isolated PostgreSQL test harness required');
  const adminURL=new URL(source);
  if(!['localhost','127.0.0.1'].includes(adminURL.hostname)||adminURL.password!=='fixture-only-password')throw Error('Fixture-only database required');
  const master=new pg.Pool({connectionString:source});
  const name='test_'+randomUUID().replaceAll('-','');
  await master.query(`CREATE DATABASE "${name}" TEMPLATE template0`);
  const url=new URL(source);url.pathname='/'+name;
  const admin=new pg.Pool({connectionString:url.toString()});
  await admin.query("CREATE SCHEMA auth; CREATE SCHEMA extensions; CREATE TABLE auth.users(id uuid PRIMARY KEY,raw_app_meta_data jsonb); CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql AS $$ SELECT null::uuid $$;");
  for(const file of ['20260929000000_discovery_admin.sql','20260929010000_catalog_criteria.sql','20260929150000_api_prisma.sql']) await admin.query(readFileSync(new URL('../../../supabase/migrations/'+file,import.meta.url),'utf8'));
  url.username='dearby_api_runtime';
  const connection=url.toString();const db=openPostgres({DATABASE_URL:connection});await verifyRuntimeRole(db);
  let clock=Date.now(); const codes=new Map<string,string>();
  const {app}=createApp(db,{otpSecret:'test-only-secret-not-used-outside-tests',guestProxySecret,now:()=>clock,
    sendCode:send??(async(email,code)=>{codes.set(email,code);})});
  await app.listen({host:'127.0.0.1',port:0});
  const address=app.server.address();assert.ok(address&&typeof address!=='string');
  const request=async(method:string,route:string,body?:unknown,token?:string)=>{
    const r=await fetch(`http://127.0.0.1:${address.port}/v1${route}`,{method,headers:{...(body!==undefined?{'content-type':'application/json'}:{}),...(token?{authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body)});
    const text=await r.text();return {status:r.status,body:text?JSON.parse(text):null,headers:r.headers};
  };
  const login=async(email:string)=>{
    const challenge=await request('POST','/auth/challenges',{email});assert.equal(challenge.status,202);
    const session=await request('POST','/auth/sessions',{challengeId:challenge.body.challengeId,code:codes.get(email)});assert.equal(session.status,200);
    return session.body as {sessionToken:string;profileId:string};
  };
  return {db,admin,connection,codes,app,request,login,setClock:(ms:number)=>{clock=ms;},advance:(ms:number)=>{clock+=ms;},close:async()=>{
    await app.close();await db.$disconnect();await admin.end();await master.query(`DROP DATABASE "${name}"`);await master.end();
  }};
}
