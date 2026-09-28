import { readFileSync } from 'node:fs';
import { parseArgs } from 'node:util';
import { openDatabase } from './database.js';
import { importLegacy } from './catalog-legacy.js';
import { officialSources, refreshSource, type SourceKey } from './catalog-sources.js';

process.umask(0o077);
const {values,positionals} = parseArgs({allowPositionals:true,options:{db:{type:'string'},source:{type:'string'}}});
const command = positionals[0];
if (!values.db || positionals.length !== 1 || !['refresh','import-legacy'].includes(command) ||
    (values.source !== undefined && (command !== 'refresh' || !Object.hasOwn(officialSources,values.source)))) {
  throw new Error('Usage: catalog <refresh|import-legacy> --db <isolated.sqlite> [--source kakao-2026|feconf-2026]');
}
const db = openDatabase(values.db);
try {
  if (command === 'import-legacy') {
    const count = importLegacy(db,JSON.parse(readFileSync(new URL('../../../shared/data/catalog-snapshot-2026-09-24.json',import.meta.url),'utf8')));
    console.log(JSON.stringify({historicalRecords:count,live:false}));
  } else {
    for (const sourceKey of values.source ? [values.source as SourceKey] : Object.keys(officialSources) as SourceKey[]) {
      const result = await refreshSource(db,sourceKey);
      console.log(JSON.stringify(result));
      if (!result.succeeded) process.exitCode = 1;
    }
  }
} finally { db.close(); }
