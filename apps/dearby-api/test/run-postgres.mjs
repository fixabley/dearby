import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { readFileSync,readdirSync } from 'node:fs';
import pg from 'pg';
const name=`dearby-prisma-test-${randomUUID().slice(0,8)}`;
let owned=false;
let url=process.env.PG_TEST_ADMIN_URL;
const docker=(...args)=>{const r=spawnSync('docker',args,{encoding:'utf8'});if(r.status!==0)throw Error('Isolated test Docker command failed');return r.stdout.trim();};
try {
 if(!url){
  docker('run','-d','--name',name,'-p','127.0.0.1::5432','--tmpfs','/var/lib/postgresql/data:rw,mode=0700',
   '-e','POSTGRES_PASSWORD=fixture-only-password','-e','JWT_SECRET=fixture-only-jwt-secret-at-least-32-characters',
   'public.ecr.aws/supabase/postgres:17.6.1.171','postgres','-D','/etc/postgresql','-c','cron.launch_active_jobs=off');
  owned=true;
  const port=docker('port',name,'5432/tcp').split(':').at(-1);
  url=`postgresql://postgres:fixture-only-password@127.0.0.1:${port}/postgres`;
 }
 const parsed=new URL(url);
 if(!['127.0.0.1','localhost'].includes(parsed.hostname)||parsed.password!=='fixture-only-password')throw Error('Tests require isolated loopback fixture credentials');
 let pool;
 for(let i=0;i<60;i++){
  pool=new pg.Pool({connectionString:url,connectionTimeoutMillis:1000});
  try{await pool.query('SELECT 1');break;}catch{await pool.end();pool=undefined;await new Promise(r=>setTimeout(r,500));}
 }
 if(!pool)throw Error('Isolated PostgreSQL unavailable');
 // A fresh disposable cluster only. Refuse to reset/reuse any application schema.
 const exists=await pool.query("SELECT 1 FROM pg_namespace WHERE nspname='dearby_api'");
 if(exists.rowCount)throw Error('Fixture cluster is not fresh');
 for(const file of readdirSync('../../supabase/migrations').filter(f=>f.endsWith('.sql')).sort()){
  await pool.query(readFileSync(`../../supabase/migrations/${file}`,'utf8'));
 }
 await pool.query("SELECT cron.alter_job(jobid,active:=false) FROM cron.job");
 await pool.query("UPDATE public.catalog_collection_settings SET enabled=false");
 await pool.query("ALTER ROLE dearby_api_runtime LOGIN PASSWORD 'fixture-only-password'");
 await pool.end();
 const result=spawnSync(process.execPath,['--import','tsx','--test','--test-concurrency=1',...readdirSync('test').filter(f=>f.endsWith('.test.ts')).map(f=>'test/'+f)],{stdio:'inherit',env:{...process.env,NODE_ENV:'test',PG_TEST_ADMIN_URL:url}});
 process.exitCode=result.status??1;
} catch {console.error('Isolated PostgreSQL test setup failed (no production connections permitted)');process.exitCode=1;}
finally {if(owned)docker('rm','-f',name);}
