import { openDatabase } from './database.js';
import { createApp } from './app.js';
import { smtpMailer } from './mail.js';
process.umask(0o077);
const db = openDatabase(process.env.DATABASE_PATH || './var/dearby.sqlite');
const { app } = createApp(db, {otpSecret: process.env.OTP_SECRET || '', sendCode: smtpMailer(process.env)});
app.addHook('onClose', async () => { db.close(); });
try { await app.listen({host: process.env.HOST || '127.0.0.1', port: Number(process.env.PORT || 3000)}); }
catch { console.error('API startup failed; check configuration'); process.exitCode = 1; await app.close(); }
for (const signal of ['SIGINT', 'SIGTERM'] as const) process.once(signal, () => { void app.close(); });
