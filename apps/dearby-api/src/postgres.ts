import { readFileSync } from 'node:fs';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient, type Prisma } from './generated/prisma/client.js';
export type DB = PrismaClient;
export type Transaction = Prisma.TransactionClient;
export function openPostgres(env: NodeJS.ProcessEnv): DB {
  let url: URL;
  try { url = new URL(env.DATABASE_URL ?? ''); } catch { throw new Error('PostgreSQL connection configuration required'); }
  if (!['postgres:', 'postgresql:'].includes(url.protocol)) throw new Error('PostgreSQL connection required');
  const local = ['localhost','127.0.0.1','[::1]'].includes(url.hostname);
  // Do not let pg connection-string SSL options override certificate verification.
  url.search=''; // Adapter configuration, not URI options, owns TLS/timeouts.
  const adapter = new PrismaPg({connectionString:url.toString(),max:5,connectionTimeoutMillis:5000,
    idleTimeoutMillis:10000,statement_timeout:8000,query_timeout:10000,
    ssl:local ? false : {rejectUnauthorized:true,...(env.DATABASE_CA_FILE ? {ca:readFileSync(env.DATABASE_CA_FILE,'utf8')} : {})},
    application_name:'dearby-api'}, {schema:'dearby_api'});
  return new PrismaClient({adapter,log:[],errorFormat:'minimal'});
}
// Preserve SQLite's serialized write semantics across API processes, including count-based quotas.
// The fixed application lock is transaction-scoped; never held across SMTP/network work.
export function write<T>(db: DB, action: (tx: Transaction) => Promise<T>): Promise<T> {
  return db.$transaction(async tx => {
    await tx.$queryRaw`SELECT 1 FROM pg_advisory_xact_lock(${7240929301n}::bigint)`;
    return action(tx);
  }, {maxWait:10000,timeout:15000});
}
export async function verifyRuntimeRole(db: DB) {
  const [role] = await db.$queryRaw<{safe:boolean}[]>`SELECT current_user = 'dearby_api_runtime'
    AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolinherit AND NOT rolreplication
    AND NOT EXISTS(SELECT 1 FROM pg_auth_members WHERE member=pg_roles.oid)
    AND NOT EXISTS(SELECT 1 FROM pg_class WHERE relowner=pg_roles.oid) AS safe
    FROM pg_roles WHERE rolname=current_user`;
  if (!role?.safe) throw new Error('Dedicated least-privilege API database role required');
}
