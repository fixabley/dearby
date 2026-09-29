// The preserved SQLite catalog maintenance tools are offline-only, not the API runtime.
import Fastify from 'fastify';
import { mkdtempSync,rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDatabase,type DB } from '../src/database.js';
import { readCatalog } from '../src/catalog.js';
export function offlineApp(db:DB,now:()=>number) {
 const app=Fastify();app.get('/v1/catalog',async(_request,reply)=>{reply.header('Cache-Control','no-store');return readCatalog(db,now());});return {app};
}
export async function fixture(){
 const directory=mkdtempSync(join(tmpdir(),'dearby-offline-'));const path=join(directory,'test.sqlite');const db=openDatabase(path);let clock=Date.now();const {app}=offlineApp(db,()=>clock);
 await app.listen({host:'127.0.0.1',port:0});const address=app.server.address();if(!address||typeof address==='string')throw Error('No listener');
 return {db,path,app,setClock:(ms:number)=>{clock=ms;},advance:(ms:number)=>{clock+=ms;},request:async(method:string,route:string)=>{const r=await fetch(`http://127.0.0.1:${address.port}/v1${route}`,{method});return {status:r.status,body:await r.json(),headers:r.headers};},close:async()=>{await app.close();if(db.open)db.close();rmSync(directory,{recursive:true,force:true});}};
}
