import SQLite from 'better-sqlite3';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { openPostgres, write, type DB, type Transaction } from './postgres.js';
type Row = Record<string,string|number|bigint|boolean|null>;
type Model = {findMany():Promise<Row[]>;createMany(args:{data:Row[]}):Promise<unknown>};
const tables = [
  ['profiles','profile'],['challenges','challenge'],['rate_limits','rateLimit'],['sessions','session'],
  ['cards','card'],['receipts','receipt'],['exchanges','exchange'],['guest_sessions','guestSession'],['guest_cards','guestCard'],
  ['catalog_organizations','legacyOrganization'],['catalog_programs','legacyProgram'],['catalog_activities','legacyActivity'],
  ['catalog_refreshes','legacyRefresh'],['migrations','legacyMigration'],
] as const;
const ordered = new Set(['cards','receipts','guest_cards']);
const bools = new Set(['consumed','revoked','succeeded']);
const bigints = new Set(['expiresAt','windowStart','ordinal']);
const camel = (key:string) => key.replace(/_([a-z])/g,(_,letter:string)=>letter.toUpperCase());
const canonical = (rows:Row[]) => rows.map(row => JSON.stringify(Object.fromEntries(Object.keys(row).sort().map(key=>[key,typeof row[key]==='bigint'?String(row[key]):row[key]])))).sort().join('\n');
const summary = (rows:Row[]) => ({count:rows.length,sha256:createHash('sha256').update(canonical(rows)).digest('hex')});
const model = (db:DB|Transaction,key:string) => (db as unknown as Record<string,Model>)[key];
export function readSQLite(path:string): Map<string,Row[]> {
  const db = new SQLite(path,{readonly:true,fileMustExist:true}); db.defaultSafeIntegers();
  try {
    if (db.pragma('integrity_check',{simple:true}) !== 'ok' || (db.pragma('foreign_key_check') as unknown[]).length) throw new Error('SQLite integrity failed');
    return db.transaction(() => {
      const present=(db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").all() as {name:string}[]).map(r=>r.name).sort();
      if (JSON.stringify(present)!==JSON.stringify(tables.map(([name])=>name).sort())) throw new Error('Unexpected SQLite schema; refusing partial import');
      return new Map(tables.map(([table]) => {
        // Identifiers come only from the fixed table allowlist, never CLI input.
        const rows=db.prepare(`SELECT ${ordered.has(table)?'rowid AS ordinal,':''}* FROM "${table}"`).all() as Row[];
        return [table,rows.map(row=>Object.fromEntries(Object.entries(row).map(([name,value])=>{
          const key=camel(name);
          if (bools.has(key) && value!==0n && value!==1n) throw new Error('Unexpected SQLite boolean');
          if(typeof value==='bigint'&&!bigints.has(key)&&!bools.has(key)&&!Number.isSafeInteger(Number(value))) throw new Error('Unsafe SQLite integer');
          return [key,bools.has(key)?value===1n:bigints.has(key)?BigInt(value as bigint):typeof value==='bigint'?Number(value):value];
        })))];
      }));
    })();
  } finally { db.close(); }
}
export async function importSQLite(db:DB,path:string,apply=false) {
  const source=readSQLite(path);
  const inspect=async (tx:DB|Transaction) => {
    const results=[];
    for (const [table,key] of tables) {
      const before=summary(source.get(table)!); const target=summary(await model(tx,key).findMany());
      results.push({table,source:before,target,equal:before.count===target.count&&before.sha256===target.sha256});
    }
    return results;
  };
  if (!apply) return {mode:'dry-run',tables:await inspect(db)};
  return write(db,async tx=>{
    const before=await inspect(tx);
    if (before.every(t=>t.equal)) return {mode:'unchanged',tables:before};
    if (before.some(t=>t.target.count!==0)) throw new Error('Target differs; refusing merge or overwrite');
    for (const [table,key] of tables) {
      const rows=source.get(table)!;
      for(let start=0;start<rows.length;start+=500) await model(tx,key).createMany({data:rows.slice(start,start+500)});
    }
    // Fixed sequence names only; parameterized values, nonempty imports require a temporary sequence UPDATE grant, revoked by the operator afterward.
    if(source.get('cards')!.length) await tx.$queryRaw`SELECT setval('dearby_api.cards_ordinal_seq',COALESCE((SELECT MAX(ordinal) FROM dearby_api.cards),0)+1,false)`;
    if(source.get('receipts')!.length) await tx.$queryRaw`SELECT setval('dearby_api.receipts_ordinal_seq',COALESCE((SELECT MAX(ordinal) FROM dearby_api.receipts),0)+1,false)`;
    if(source.get('guest_cards')!.length) await tx.$queryRaw`SELECT setval('dearby_api.guest_cards_ordinal_seq',COALESCE((SELECT MAX(ordinal) FROM dearby_api.guest_cards),0)+1,false)`;
    const after=await inspect(tx);
    if (!after.every(t=>t.equal)) throw new Error('Import verification failed');
    return {mode:'imported',tables:after};
  });
}
if (process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  process.umask(0o077);
  const args=process.argv.slice(2); const path=args[args.indexOf('--source')+1];
  if (!args.includes('--source') || !path || args.some(a=>a.startsWith('--')&&!['--source','--apply'].includes(a))) {
    console.error('Usage: import-sqlite --source <frozen-backup.sqlite> [--apply]');process.exitCode=1;
  } else {
    let db:DB|undefined;
    try {db=openPostgres(process.env);console.log(JSON.stringify(await importSQLite(db,path,args.includes('--apply'))));}
    catch {console.error('SQLite import failed; source and target preserved, check protected diagnostics');process.exitCode=1;}
    finally {await db?.$disconnect();}
  }
}
