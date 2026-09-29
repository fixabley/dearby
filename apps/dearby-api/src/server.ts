import { openPostgres, verifyRuntimeRole } from './postgres.js';
import { createApp } from './app.js';
import { smtpMailer } from './mail.js';
process.umask(0o077);
async function main() {
  const db = openPostgres(process.env);
  try {
    await verifyRuntimeRole(db);
    const {app} = createApp(db,{otpSecret:process.env.OTP_SECRET || '',sendCode:smtpMailer(process.env),guestProxySecret:process.env.GUEST_PROXY_SECRET});
    app.addHook('onClose',async () => { await db.$disconnect(); });
    await app.listen({host:process.env.HOST || '127.0.0.1',port:Number(process.env.PORT || 3000)});
    for (const signal of ['SIGINT','SIGTERM'] as const) process.once(signal,() => { void app.close(); });
  } catch { await db.$disconnect(); throw new Error('API startup failed'); }
}
main().catch(() => { console.error('API startup failed; check configuration'); process.exitCode=1; });
