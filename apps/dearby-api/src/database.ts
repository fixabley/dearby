import Database from 'better-sqlite3';
import { mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { dirname } from 'node:path';

export function openDatabase(path: string) {
  if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  const db = new Database(path);
  db.pragma('foreign_keys = ON');
  db.pragma('journal_mode = WAL');
  db.pragma('busy_timeout = 5000');
  db.exec('CREATE TABLE IF NOT EXISTS migrations (name TEXT PRIMARY KEY)');
  const directory = new URL('../migrations/', import.meta.url);
  for (const name of readdirSync(directory).filter(n => n.endsWith('.sql')).sort()) {
    db.transaction(() => {
      if (db.prepare('SELECT name FROM migrations WHERE name = ?').get(name)) return;
      db.exec(readFileSync(new URL(name, directory), 'utf8'));
      db.prepare('INSERT INTO migrations VALUES (?)').run(name);
    }).immediate();
  }
  return db;
}
export type DB = ReturnType<typeof openDatabase>;
