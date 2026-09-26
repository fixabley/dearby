import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
import { openDatabase } from '../src/database.js';
import { createApp } from '../src/app.js';
import type { SendCode } from '../src/mail.js';
export async function fixture(send?: SendCode) {
  if (process.env.NODE_ENV !== 'test') throw new Error('Test mail sink requires NODE_ENV=test');
  const directory = mkdtempSync(join(tmpdir(), 'dearby-test-'));
  const path = join(directory, 'test.sqlite');
  let clock = Date.now();
  const codes = new Map<string, string>();
  const db = openDatabase(path);
  const {app} = createApp(db, {otpSecret:'test-only-secret-not-used-outside-tests', now:() => clock,
    sendCode: send ?? (async (email, code) => { codes.set(email, code); }),
  });
  await app.listen({host:'127.0.0.1',port:0});
  const address = app.server.address();
  if (!address || typeof address === 'string') throw new Error('Missing server address');
  const request = async (method:string, route:string, body?:unknown, token?:string) => {
    const response = await fetch(`http://127.0.0.1:${address.port}/v1${route}`, {method,
      headers:{...(body !== undefined ? {'content-type':'application/json'} : {}), ...(token ? {authorization:`Bearer ${token}`} : {})},
      body:body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    return {status:response.status, body:text ? JSON.parse(text) : null, headers:response.headers};
  };
  const login = async (email:string) => {
    const challenge = await request('POST','/auth/challenges',{email});
    assert.equal(challenge.status,202);
    const session = await request('POST','/auth/sessions',{challengeId:challenge.body.challengeId,code:codes.get(email)});
    assert.equal(session.status,200);
    return session.body as {sessionToken:string; profileId:string};
  };
  return {db,path,codes,app,request,login, advance:(ms:number) => { clock += ms; }, close:async () => {
    await app.close(); db.close(); rmSync(directory,{recursive:true,force:true});
  }};
}
