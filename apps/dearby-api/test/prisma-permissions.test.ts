import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fixture} from './helpers.js';
import {openPostgres,verifyRuntimeRole} from '../src/postgres.js';
test('runtime owns no objects, cannot bypass RLS/DDL/public/Auth; browser roles cannot access private tables',async t=>{
 const f=await fixture();t.after(f.close);
 await verifyRuntimeRole(f.db);
 for(const sql of [
  'CREATE TABLE dearby_api.forbidden(id text)',
  'TRUNCATE dearby_api.profiles',
  "UPDATE public.catalog_organizations SET name='forbidden'",
  'SELECT * FROM auth.users',
  'SET ROLE postgres',
  "SELECT setval('dearby_api.cards_ordinal_seq',1,false)",
 ]) await assert.rejects(f.db.$executeRawUnsafe(sql)); // Fixed test-only statements.
 const owned=await f.admin.query("SELECT count(*)::int AS n FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner WHERE r.rolname='dearby_api_runtime'");assert.equal(owned.rows[0].n,0);
 const policies=await f.admin.query("SELECT count(*)::int AS n FROM pg_tables WHERE schemaname='dearby_api' AND rowsecurity");assert.equal(policies.rows[0].n,17);
 for(const role of ['anon','authenticated','service_role']){
  const conn=await f.admin.connect();
  try {await conn.query('SET ROLE '+role);await assert.rejects(conn.query('SELECT * FROM dearby_api.profiles'));await assert.rejects(conn.query('SELECT * FROM dearby_api.guest_sessions'));await assert.rejects(conn.query('SELECT * FROM dearby_api.card_shares'));await assert.rejects(conn.query('SELECT * FROM dearby_api.guest_card_shares'));await assert.rejects(conn.query('SELECT * FROM dearby_api.guest_handoffs'));}
  finally {await conn.query('RESET ROLE');conn.release();}
 }
 const admin=openPostgres({DATABASE_URL:f.connection.replace('dearby_api_runtime:','postgres:')});
 try {await assert.rejects(verifyRuntimeRole(admin));}finally{await admin.$disconnect();}
});
