// Local native/API integration only. No real mail and no public inbox endpoint.
import { randomBytes } from 'node:crypto';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createApp } from '../apps/dearby-api/dist/app.js';
import { openDatabase } from '../apps/dearby-api/dist/database.js';

if (process.env.NODE_ENV !== 'test') throw new Error('Run only with NODE_ENV=test');
process.umask(0o077);
const directory = mkdtempSync(join(tmpdir(), 'dearby-native-integration-'));
const inbox = join(directory, 'mail');
mkdirSync(inbox, { mode: 0o700 });
const accounts = new Set(['ios@example.test', 'android@example.test']);
const db = openDatabase(join(directory, 'test.sqlite'));
const { app } = createApp(db, {
  otpSecret: randomBytes(32).toString('hex'),
  sendCode: async (email, code) => {
    if (!accounts.has(email)) throw new Error('Only isolated test accounts are supported');
    writeFileSync(join(inbox, `${email}.json`), JSON.stringify({ code }), { mode: 0o600 });
  },
});
app.addHook('onClose', async () => {
  db.close();
  rmSync(directory, { recursive: true, force: true });
});
try {
  if (process.argv.includes('--catalog')) {
    const { importLegacy } = await import('../apps/dearby-api/dist/catalog-legacy.js');
    const { officialSources, refreshSource } = await import('../apps/dearby-api/dist/catalog-sources.js');
    const { readCatalog } = await import('../apps/dearby-api/dist/catalog.js');
    importLegacy(db, JSON.parse(readFileSync(new URL('../shared/data/catalog-snapshot-2026-09-24.json', import.meta.url), 'utf8')));
    for (const source of Object.keys(officialSources)) console.log(JSON.stringify(await refreshSource(db, source)));
    const catalog = readCatalog(db);
    console.log(JSON.stringify({ activities: catalog.activities.length, recruiting: catalog.activities.filter(activity => activity.isRecruiting).length }));
  }
  const address = await app.listen({ host: '127.0.0.1', port: 0 });
  // Metadata only. Never print inbox contents, OTPs, session tokens or profiles.
  console.log(JSON.stringify({ apiOrigin: address, inboxDirectory: inbox, testAccounts: [...accounts] }));
} catch (error) {
  await app.close();
  throw error;
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { void app.close(); });
